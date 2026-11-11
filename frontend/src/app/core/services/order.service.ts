import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Order, OrderSummary } from '../models/order.model';

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface GetOrdersParams {
  status?: string;
  customerEmail?: string;
  page: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.orderServiceUrl}/api/v1/orders`;

  getOrders(params: GetOrdersParams): Observable<Page<OrderSummary>> {
    let httpParams = new HttpParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, String(value));
      }
    }
    return this.http.get<Page<OrderSummary>>(this.baseUrl, { params: httpParams });
  }

  getOrder(id: string): Observable<Order> {
    return this.http.get<Order>(`${this.baseUrl}/${id}`);
  }

  cancelOrder(id: string): Observable<Order> {
    return this.http.patch<Order>(`${this.baseUrl}/${id}/cancel`, {});
  }
}
