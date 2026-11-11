import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatChipsModule } from '@angular/material/chips';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { OrderStatus, OrderSummary } from '../../../core/models/order.model';
import { OrderService, Page } from '../../../core/services/order.service';

const EMPTY_PAGE: Page<OrderSummary> = { content: [], totalElements: 0, totalPages: 0, number: 0 };
const PAGE_SIZE = 20;

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [
    AsyncPipe,
    DatePipe,
    ReactiveFormsModule,
    MatChipsModule,
    MatInputModule,
    MatPaginatorModule,
    MatSelectModule,
    MatTableModule
  ],
  templateUrl: './order-list.component.html',
  styleUrl: './order-list.component.scss'
})
export class OrderListComponent {
  private readonly orderService = inject(OrderService);
  private readonly router = inject(Router);

  readonly statuses: OrderStatus[] = ['PENDING', 'PAID', 'PAYMENT_FAILED', 'CANCELLED'];
  readonly displayedColumns = ['id', 'customerEmail', 'totalAmount', 'status', 'createdAt'];
  readonly pageSize = PAGE_SIZE;

  readonly statusControl = new FormControl<OrderStatus | ''>('');
  readonly emailControl = new FormControl('');

  private pageIndex = 0;
  orders$: Observable<Page<OrderSummary>> = this.fetch();

  constructor() {
    this.statusControl.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.onFilterChange());
    this.emailControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.onFilterChange());
  }

  onPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.orders$ = this.fetch();
  }

  onRowClick(order: OrderSummary): void {
    this.router.navigate(['/orders', order.id]);
  }

  formatAmount(amount: number): string {
    return amount.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  private onFilterChange(): void {
    this.pageIndex = 0;
    this.orders$ = this.fetch();
  }

  private fetch(): Observable<Page<OrderSummary>> {
    return this.orderService
      .getOrders({
        status: this.statusControl.value || undefined,
        customerEmail: this.emailControl.value || undefined,
        page: this.pageIndex,
        size: this.pageSize
      })
      .pipe(catchError(() => of(EMPTY_PAGE)));
  }
}
