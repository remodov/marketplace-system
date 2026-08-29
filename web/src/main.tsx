import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import Shop from './components/Shop'
import { Funnel, InMemorySink } from './funnel/funnel'

const funnel = new Funnel(new InMemorySink())

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Shop funnel={funnel} />
  </StrictMode>,
)
