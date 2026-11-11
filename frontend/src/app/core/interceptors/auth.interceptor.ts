import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authReq = req.clone({
    setHeaders: {
      Authorization: `Bearer ${environment.adminToken}`,
      'X-Correlation-Id': crypto.randomUUID()
    }
  });
  return next(authReq);
};
