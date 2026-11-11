import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Payment } from '../models/payment.model';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.paymentServiceUrl}/api/v1/payments`;

  getPayment(orderId: string): Observable<Payment> {
    return this.http.get<Payment>(`${this.baseUrl}/${orderId}`);
  }
}
