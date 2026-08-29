import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Shop from '../components/Shop'
import { Funnel, InMemorySink } from '../funnel/funnel'

const products = [
  { id: 'p-1', title: 'Беспроводная мышь', price: '1990.00' },
  { id: 'p-2', title: 'Механическая клавиатура', price: '5400.00' },
]

const orderScreen = {
  orderId: 'o-1', status: 'PAID', total: '1990.00', paymentStatus: 'CAPTURED',
  items: [{ productId: 'p-1', title: 'Беспроводная мышь', quantity: 1, price: '1990.00' }],
}

let posted: { url: string; body: unknown; headers: Record<string, string> }[] = []

function mockApi() {
  posted = []
  vi.stubGlobal('fetch', vi.fn((url: string, init?: RequestInit) => {
    const u = String(url)
    if (init?.method === 'POST') {
      posted.push({ url: u, body: JSON.parse(String(init.body)), headers: init.headers as Record<string, string> })
      return Promise.resolve(new Response(JSON.stringify({ id: 'o-1' }),
        { status: 201, headers: { 'content-type': 'application/json' } }))
    }
    const body = u.includes('/screens/order/') ? orderScreen : products
    return Promise.resolve(new Response(JSON.stringify(body),
      { status: 200, headers: { 'content-type': 'application/json' } }))
  }))
}

describe('витрина', () => {
  beforeEach(mockApi)

  it('показывает каталог и считает корзину', async () => {
    render(<Shop funnel={new Funnel(new InMemorySink())} />)

    await waitFor(() => expect(screen.getByText('Беспроводная мышь')).toBeInTheDocument())
    await userEvent.click(screen.getAllByRole('button', { name: 'В корзину' })[0])
    await userEvent.click(screen.getAllByRole('button', { name: 'В корзину' })[0])

    expect(screen.getByText('В корзине: 2')).toBeInTheDocument()
  })

  it('оформление создаёт заказ с ключом идемпотентности и показывает статус', async () => {
    render(<Shop funnel={new Funnel(new InMemorySink())} newKey={() => 'key-1'} />)

    await waitFor(() => expect(screen.getByText('Беспроводная мышь')).toBeInTheDocument())
    await userEvent.click(screen.getAllByRole('button', { name: 'В корзину' })[0])
    await userEvent.click(screen.getByRole('button', { name: 'Оформить' }))

    await waitFor(() => expect(screen.getByRole('heading', { name: /Заказ PAID/ })).toBeInTheDocument())
    expect(posted[0].headers['Idempotency-Key']).toBe('key-1')
    expect(posted[0].body).toEqual({ items: [{ productId: 'p-1', quantity: 1 }] })
  })

  it('путь покупки виден в воронке целиком', async () => {
    const sink = new InMemorySink()
    render(<Shop funnel={new Funnel(sink)} newKey={() => 'key-2'} />)

    await waitFor(() => expect(screen.getByText('Беспроводная мышь')).toBeInTheDocument())
    await userEvent.click(screen.getAllByRole('button', { name: 'В корзину' })[0])
    await userEvent.click(screen.getByRole('button', { name: 'Оформить' }))
    await waitFor(() => expect(screen.getByRole('heading', { name: /Заказ PAID/ })).toBeInTheDocument())

    const steps = sink.events.map(e => e.step)
    expect(steps.filter(s => s === 'product_viewed')).toHaveLength(2)
    expect(steps).toContain('added_to_cart')
    expect(steps).toContain('checkout_started')
    expect(steps).toContain('order_paid')
  })

  it('пустую корзину оформить нельзя', async () => {
    render(<Shop funnel={new Funnel(new InMemorySink())} />)

    await waitFor(() => expect(screen.getByText('Беспроводная мышь')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Оформить' })).toBeDisabled()
  })
})
