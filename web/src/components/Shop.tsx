import { useCallback, useEffect, useState } from 'react'
import { createOrder, loadCatalog, loadOrderScreen, type OrderScreen, type Product } from '../api/marketplace'
import { Funnel } from '../funnel/funnel'

interface Props {
  funnel: Funnel
  newKey?: () => string
}

type CartLine = { product: Product; quantity: number }

export default function Shop({ funnel, newKey = () => crypto.randomUUID() }: Props) {
  const [products, setProducts] = useState<Product[] | null>(null)
  const [cart, setCart] = useState<CartLine[]>([])
  const [screen, setScreen] = useState<OrderScreen | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    loadCatalog()
      .then(list => {
        setProducts(list)
        list.forEach(p => funnel.track('product_viewed', { productId: p.id }))
      })
      .catch(e => setError(String(e)))
  }, [funnel])

  const addToCart = useCallback((product: Product) => {
    setCart(prev => {
      const line = prev.find(l => l.product.id === product.id)
      return line
        ? prev.map(l => (l.product.id === product.id ? { ...l, quantity: l.quantity + 1 } : l))
        : [...prev, { product, quantity: 1 }]
    })
    funnel.track('added_to_cart', { productId: product.id })
  }, [funnel])

  const checkout = useCallback(async () => {
    funnel.track('checkout_started')
    try {
      const created = await createOrder(
        cart.map(l => ({ productId: l.product.id, quantity: l.quantity })),
        newKey(),
      )
      const orderScreen = await loadOrderScreen(created.id)
      setScreen(orderScreen)
      if (orderScreen.paymentStatus === 'CAPTURED') {
        funnel.track('order_paid', { orderId: created.id })
      }
    } catch (e) {
      setError(String(e))
    }
  }, [cart, newKey, funnel])

  if (error) return <p role="alert">Ошибка: {error}</p>
  if (!products) return <p>Загрузка…</p>

  if (screen) {
    return (
      <section>
        <h1>Заказ {screen.status}</h1>
        <p>Оплата: {screen.paymentStatus}</p>
        <ul>
          {screen.items.map(i => (
            <li key={i.productId}>{i.title} × {i.quantity}</li>
          ))}
        </ul>
        <p>Итого: {screen.total}</p>
      </section>
    )
  }

  return (
    <section>
      <h1>Каталог</h1>
      <ul>
        {products.map(p => (
          <li key={p.id}>
            <span>{p.title}</span> <span>{p.price} ₽</span>
            <button onClick={() => addToCart(p)}>В корзину</button>
          </li>
        ))}
      </ul>
      <p>В корзине: {cart.reduce((sum, l) => sum + l.quantity, 0)}</p>
      <button disabled={cart.length === 0} onClick={checkout}>Оформить</button>
    </section>
  )
}
