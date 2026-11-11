export type OrderStatus = 'PENDING' | 'PAID' | 'PAYMENT_FAILED' | 'CANCELLED';

export interface OrderItem {
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface Order {
  id: string;
  status: OrderStatus;
  totalAmount: number;
  customerEmail: string;
  items: OrderItem[];
  createdAt: string;
  updatedAt: string;
}

export interface OrderSummary {
  id: string;
  status: OrderStatus;
  totalAmount: number;
  customerEmail: string;
  createdAt: string;
  updatedAt: string;
}
