export interface Product {
  id: string
  title: string
  price: string
  available?: number
}

export interface OrderScreen {
  orderId: string
  status: string
  total: string
  paymentStatus: string
  items: { productId: string; title: string; quantity: number; price: string }[]
}

const BFF = import.meta.env?.VITE_BFF_URL ?? ''

async function json<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error('Запрос не удался: ' + response.status)
  }
  return response.json() as Promise<T>
}

export async function loadCatalog(): Promise<Product[]> {
  return json<Product[]>(await fetch(BFF + '/products'))
}

export async function createOrder(items: { productId: string; quantity: number }[],
                                  idempotencyKey: string): Promise<{ id: string }> {
  return json<{ id: string }>(await fetch(BFF + '/api/v1/orders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ items }),
  }))
}

export async function loadOrderScreen(orderId: string): Promise<OrderScreen> {
  return json<OrderScreen>(await fetch(BFF + '/api/v1/screens/order/' + orderId))
}
