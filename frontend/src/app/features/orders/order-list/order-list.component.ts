import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatChipsModule } from '@angular/material/chips';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { Observable, Subject, combineLatest, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, startWith, switchMap } from 'rxjs/operators';
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

  // Paginator clicks feed this subject instead of reassigning a field, so a single
  // long-lived observable (below) drives the table purely via the `| async` pipe.
  // That keeps this zoneless-safe: no manual field reassignment from an async callback.
  private readonly pageRequest$ = new Subject<number>();

  readonly orders$: Observable<Page<OrderSummary>> = combineLatest([
    this.statusControl.valueChanges.pipe(startWith(this.statusControl.value)),
    this.emailControl.valueChanges.pipe(
      startWith(this.emailControl.value),
      debounceTime(300),
      distinctUntilChanged()
    )
  ]).pipe(
    // Any filter change restarts the page-request stream at page 0, naturally
    // resetting pagination without a separate reset mechanism.
    switchMap(([status, email]) =>
      this.pageRequest$.pipe(
        startWith(0),
        switchMap((page) => this.fetch(status, email, page))
      )
    )
  );

  onPage(event: PageEvent): void {
    this.pageRequest$.next(event.pageIndex);
  }

  onRowClick(order: OrderSummary): void {
    this.router.navigate(['/orders', order.id]);
  }

  formatAmount(amount: number): string {
    return amount.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  private fetch(status: OrderStatus | '' | null, email: string | null, page: number): Observable<Page<OrderSummary>> {
    return this.orderService
      .getOrders({
        status: status || undefined,
        customerEmail: email || undefined,
        page,
        size: this.pageSize
      })
      .pipe(
        catchError((err) => {
          console.error('Failed to load orders', err);
          return of(EMPTY_PAGE);
        })
      );
  }
}
