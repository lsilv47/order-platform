import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { forkJoin, map, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { OrderStatus, OrderSummary } from '../../core/models/order.model';
import { OrderService, Page } from '../../core/services/order.service';

const EMPTY_PAGE: Page<OrderSummary> = { content: [], totalElements: 0, totalPages: 0, number: 0 };

export interface StatusTile {
  status: OrderStatus;
  label: string;
  count: number;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [AsyncPipe, MatCardModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent {
  private readonly orderService = inject(OrderService);

  private readonly statusLabels: Record<OrderStatus, string> = {
    PENDING: 'Pendentes',
    PAID: 'Pagos',
    PAYMENT_FAILED: 'Falha no pagamento',
    CANCELLED: 'Cancelados'
  };

  readonly vm$ = forkJoin({
    PENDING: this.countFor('PENDING'),
    PAID: this.countFor('PAID'),
    PAYMENT_FAILED: this.countFor('PAYMENT_FAILED'),
    CANCELLED: this.countFor('CANCELLED'),
    // shortcut: revenue sums only first 100 orders, upgrade with a dedicated API endpoint when needed
    revenuePage: this.orderService
      .getOrders({ status: 'PAID', page: 0, size: 100 })
      .pipe(catchError(() => of(EMPTY_PAGE)))
  }).pipe(
    map(({ PENDING, PAID, PAYMENT_FAILED, CANCELLED, revenuePage }) => ({
      tiles: [
        { status: 'PENDING' as OrderStatus, label: this.statusLabels.PENDING, count: PENDING },
        { status: 'PAID' as OrderStatus, label: this.statusLabels.PAID, count: PAID },
        { status: 'PAYMENT_FAILED' as OrderStatus, label: this.statusLabels.PAYMENT_FAILED, count: PAYMENT_FAILED },
        { status: 'CANCELLED' as OrderStatus, label: this.statusLabels.CANCELLED, count: CANCELLED }
      ] as StatusTile[],
      revenue: revenuePage.content.reduce((sum, o) => sum + o.totalAmount, 0)
    }))
  );

  formatAmount(amount: number): string {
    return amount.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  private countFor(status: OrderStatus) {
    return this.orderService
      .getOrders({ status, page: 0, size: 1 })
      .pipe(
        map((page) => page.totalElements),
        catchError(() => of(0))
      );
  }
}
