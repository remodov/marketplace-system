import { describe, expect, it } from 'vitest'
import { conversion, Funnel, InMemorySink, type FunnelEvent } from '../funnel/funnel'

describe('воронка', () => {
  it('пишет шаг с товаром и временем', () => {
    const sink = new InMemorySink()
    const funnel = new Funnel(sink, () => 1000)

    funnel.track('added_to_cart', { productId: 'p-1' })

    expect(sink.events).toEqual([{ step: 'added_to_cart', productId: 'p-1', at: 1000 }])
  })

  it('считает долю дошедших до каждого шага', () => {
    const events: FunnelEvent[] = [
      ...Array.from({ length: 10 }, (): FunnelEvent => ({ step: 'product_viewed', at: 1 })),
      ...Array.from({ length: 4 }, (): FunnelEvent => ({ step: 'added_to_cart', at: 2 })),
      ...Array.from({ length: 2 }, (): FunnelEvent => ({ step: 'checkout_started', at: 3 })),
      { step: 'order_paid', at: 4 },
    ]

    const funnel = conversion(events)

    expect(funnel).toEqual([
      { step: 'product_viewed', count: 10, ofPrevious: 100 },
      { step: 'added_to_cart', count: 4, ofPrevious: 40 },
      { step: 'checkout_started', count: 2, ofPrevious: 50 },
      { step: 'order_paid', count: 1, ofPrevious: 50 },
    ])
  })

  it('шаг, до которого никто не дошёл, не делит на ноль', () => {
    const funnel = conversion([{ step: 'product_viewed', at: 1 }])

    expect(funnel[1]).toEqual({ step: 'added_to_cart', count: 0, ofPrevious: 0 })
    expect(funnel[3]).toEqual({ step: 'order_paid', count: 0, ofPrevious: 0 })
  })
})
