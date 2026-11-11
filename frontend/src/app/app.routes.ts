import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'orders', pathMatch: 'full' },
  {
    path: 'orders',
    loadComponent: () =>
      import('./features/orders/order-list/order-list.component').then((m) => m.OrderListComponent)
  },
  {
    path: 'orders/:id',
    loadComponent: () =>
      import('./features/orders/order-detail/order-detail.component').then((m) => m.OrderDetailComponent)
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent)
  }
];
