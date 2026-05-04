// src/app/app.routes.ts
import { Routes } from '@angular/router';
import { HomeLandingComponent } from './features/auth/home-landing/home-landing.component';
import { LoginComponent } from './features/auth/login.component';
import { RegisterComponent } from './features/auth/register.component';

export const routes: Routes = [
  { path: '', component: HomeLandingComponent },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: '**', redirectTo: '' }
];
