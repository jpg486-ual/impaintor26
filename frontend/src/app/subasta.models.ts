export interface Subasta {
  id: number;
  nombre: string;
}

export interface Familia {
  id: number;
  nombre: string;
}

export interface Producto {
  id: number;
  familia_id: number;
  nombre: string;
  url: string | null;
}

export interface PrecioSubasta {
  subasta_id: number;
  fecha: string;
  producto_id: number;
  corte: number;
  precio: number;
}

export interface PrecioTablaFila {
  familia: string;
  producto: string;
  corte: number;
  precio: number;
}