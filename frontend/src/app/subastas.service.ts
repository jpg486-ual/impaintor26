import {Injectable} from '@angular/core';
import {Familia, PrecioSubasta, Producto, Subasta} from './subasta.models';
@Injectable({
  providedIn: 'root',
})
export class SubastasService {
  private readonly baseUrl = 'http://localhost:8080/api';

  private toNumber(value: unknown): number {
    return typeof value === 'number' ? value : Number(value);
  }

  private normalizeSubasta(subasta: Subasta): Subasta {
    return {
      ...subasta,
      id: this.toNumber(subasta.id),
    };
  }

  private normalizeFamilia(familia: Familia): Familia {
    return {
      ...familia,
      id: this.toNumber(familia.id),
    };
  }

  private normalizeProducto(producto: Producto): Producto {
    return {
      ...producto,
      id: this.toNumber(producto.id),
      familia_id: this.toNumber(producto.familia_id),
    };
  }

  private normalizePrecio(precio: PrecioSubasta): PrecioSubasta {
    return {
      ...precio,
      subasta_id: this.toNumber(precio.subasta_id),
      producto_id: this.toNumber(precio.producto_id),
      corte: this.toNumber(precio.corte),
      precio: this.toNumber(precio.precio),
    };
  }

  private async fetchCollection<T>(resource: string): Promise<T[]> {
    const response = await fetch(`${this.baseUrl}/${resource}`);
    if (!response.ok) {
      throw new Error(`No se pudo cargar ${resource} (${response.status})`);
    }

    return ((await response.json()) as T[]) ?? [];
  }

  async getSubastas(): Promise<Subasta[]> {
    const subastas = await this.fetchCollection<Subasta>('subastas');
    return subastas.map((subasta) => this.normalizeSubasta(subasta));
  }

  async getSubastaById(id: number): Promise<Subasta | undefined> {
    const response = await fetch(`${this.baseUrl}/subastas/${id}`);
    if (response.status === 404) {
      return undefined;
    }
    if (!response.ok) {
      throw new Error(`No se pudo cargar la subasta ${id} (${response.status})`);
    }

    return this.normalizeSubasta((await response.json()) as Subasta);
  }

  async getFamilias(): Promise<Familia[]> {
    const familias = await this.fetchCollection<Familia>('familias');
    return familias.map((familia) => this.normalizeFamilia(familia));
  }

  async getProductos(): Promise<Producto[]> {
    const productos = await this.fetchCollection<Producto>('productos');
    return productos.map((producto) => this.normalizeProducto(producto));
  }

  async getPreciosBySubasta(subastaId: number): Promise<PrecioSubasta[]> {
    const response = await fetch(`${this.baseUrl}/preciosubasta?subasta_id=${subastaId}`);
    if (!response.ok) {
      throw new Error(
        `No se pudieron cargar precios para subasta ${subastaId} (${response.status})`,
      );
    }

    const precios = ((await response.json()) as PrecioSubasta[]) ?? [];
    return precios.map((precio) => this.normalizePrecio(precio));
  }
}