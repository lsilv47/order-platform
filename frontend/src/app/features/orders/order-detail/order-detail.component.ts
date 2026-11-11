import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, map, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Order } from '../../../core/models/order.model';
import { Payment } from '../../../core/models/payment.model';
import { OrderService } from '../../../core/services/order.service';
import { PaymentService } from '../../../core/services/payment.service';

export interface TimelineEvent {
  label: string;
  date: string;
  status: 'done' | 'error' | 'pending';
}

@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [AsyncPipe, DatePipe, MatChipsModule, MatIconModule, MatProgressSpinnerModule, MatTableModule],
  templateUrl: './order-detail.component.html',
  styleUrl: './order-detail.component.scss'
})
export class OrderDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly orderService = inject(OrderService);
  private readonly paymentService = inject(PaymentService);

  private readonly id = this.route.snapshot.paramMap.get('id')!;
  readonly displayedColumns = ['productName', 'quantity', 'unitPrice', 'subtotal'];

  readonly vm$ = forkJoin([
    this.orderService.getOrder(this.id).pipe(
      catchError((err) => {
        console.error('Failed to load order', err);
        return of(null);
      })
    ),
    // A 404 here means "no payment yet" (intentional, not an error), but other
    // failures (network, 5xx) are still worth a log.
    this.paymentService.getPayment(this.id).pipe(
      catchError((err) => {
        console.error('Failed to load payment', err);
        return of(null);
      })
    )
  ]).pipe(
    map(([order, payment]) => ({
      order,
      payment,
      timeline: order ? this.buildTimeline(order, payment) : []
    }))
  );

  buildTimeline(order: Order, payment: Payment | null): TimelineEvent[] {
    const events: TimelineEvent[] = [{ label: 'Pedido criado', date: order.createdAt, status: 'done' }];
    if (order.status === 'PAID' && payment) {
      events.push({ label: 'Pagamento aprovado', date: payment.createdAt, status: 'done' });
    } else if (order.status === 'PAYMENT_FAILED' && payment) {
      events.push({ label: 'Pagamento falhou', date: payment.createdAt, status: 'error' });
    } else if (order.status === 'CANCELLED') {
      events.push({ label: 'Pedido cancelado', date: order.updatedAt, status: 'error' });
    } else {
      events.push({ label: 'Aguardando pagamento', date: '', status: 'pending' });
    }
    return events;
  }

  formatAmount(amount: number): string {
    return amount.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  goBack(): void {
    this.router.navigate(['/orders']);
  }
}
