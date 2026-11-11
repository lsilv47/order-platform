export interface Payment {
  id: string;
  orderId: string;
  status: string;
  amount: number;
  stripePaymentIntentId: string;
  createdAt: string;
}
