import {Component, ElementRef, Input, ViewChild, AfterViewInit, OnChanges, SimpleChanges, Output, EventEmitter} from '@angular/core';
import {CommonModule} from '@angular/common';
import {StrokeView, StrokePoint} from '../../services/game.service';

@Component({
  selector: 'app-canvas',
  standalone: true,
  imports: [CommonModule],
  template: `
    <canvas
      #drawCanvas
      [width]="canvasWidth"
      [height]="canvasHeight"
      (pointerdown)="onPointerDown($event)"
      (pointermove)="onPointerMove($event)"
      (pointerup)="onPointerUp($event)"
      (pointerleave)="onPointerUp($event)"
      [class.drawing-enabled]="drawingEnabled"
      class="game-canvas"
    ></canvas>
  `,
  styles: [`
    :host {
      display: block;
    }
    .game-canvas {
      width: 100%;
      aspect-ratio: 4 / 3;
      border-radius: 8px;
      background: #ffffff;
      touch-action: none;
      cursor: default;
      display: block;
    }
    .game-canvas.drawing-enabled {
      cursor: crosshair;
    }
  `],
})
export class CanvasComponent implements AfterViewInit, OnChanges {
  @ViewChild('drawCanvas') canvasRef!: ElementRef<HTMLCanvasElement>;

  @Input() strokes: StrokeView[] = [];
  @Input() drawingEnabled = false;
  @Input() canvasWidth = 400;
  @Input() canvasHeight = 300;

  @Output() strokeComplete = new EventEmitter<StrokePoint[]>();

  private currentStroke: StrokePoint[] = [];
  private isDrawing = false;
  private lastRenderedStrokeCount = 0;

  ngAfterViewInit(): void {
    this.redrawAll();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['strokes'] && this.canvasRef) {
      // Incremental rendering: only draw new strokes
      const newStrokes = this.strokes.slice(this.lastRenderedStrokeCount);
      if (newStrokes.length > 0 || this.strokes.length < this.lastRenderedStrokeCount) {
        // If strokes were reset (new round), redraw all
        if (this.strokes.length < this.lastRenderedStrokeCount) {
          this.redrawAll();
        } else {
          for (const stroke of newStrokes) {
            this.drawStroke(stroke.points);
          }
        }
        this.lastRenderedStrokeCount = this.strokes.length;
      }
    }
  }

  onPointerDown(event: PointerEvent): void {
    if (!this.drawingEnabled) return;
    const canvas = this.canvasRef.nativeElement;
    canvas.setPointerCapture(event.pointerId);
    this.isDrawing = true;
    this.currentStroke = [this.toNormalizedPoint(event)];
  }

  onPointerMove(event: PointerEvent): void {
    if (!this.isDrawing || !this.drawingEnabled) return;
    const point = this.toNormalizedPoint(event);
    this.currentStroke.push(point);

    // Draw live stroke segment
    const canvas = this.canvasRef.nativeElement;
    const ctx = canvas.getContext('2d');
    if (!ctx || this.currentStroke.length < 2) return;

    const from = this.scaleToCanvas(this.currentStroke[this.currentStroke.length - 2], canvas);
    const to = this.scaleToCanvas(point, canvas);

    ctx.strokeStyle = '#000';
    ctx.lineWidth = 2.5;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.moveTo(from.x, from.y);
    ctx.lineTo(to.x, to.y);
    ctx.stroke();
  }

  onPointerUp(event: PointerEvent): void {
    if (!this.isDrawing) return;
    this.isDrawing = false;

    const canvas = this.canvasRef.nativeElement;
    canvas.releasePointerCapture(event.pointerId);

    if (this.currentStroke.length > 1) {
      this.strokeComplete.emit([...this.currentStroke]);
    }
    this.currentStroke = [];
  }

  private redrawAll(): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    for (const stroke of this.strokes) {
      this.drawStroke(stroke.points);
    }

    this.lastRenderedStrokeCount = this.strokes.length;
  }

  private drawStroke(points: StrokePoint[]): void {
    if (points.length < 2) return;
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.strokeStyle = '#000';
    ctx.lineWidth = 2.5;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();

    const start = this.scaleToCanvas(points[0], canvas);
    ctx.moveTo(start.x, start.y);

    for (let i = 1; i < points.length; i++) {
      const p = this.scaleToCanvas(points[i], canvas);
      ctx.lineTo(p.x, p.y);
    }

    ctx.stroke();
  }

  private scaleToCanvas(point: StrokePoint, canvas: HTMLCanvasElement): {x: number; y: number} {
    return {
      x: (point.x / 1000) * canvas.width,
      y: (point.y / 1000) * canvas.height,
    };
  }

  private toNormalizedPoint(event: PointerEvent): StrokePoint {
    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    return {
      x: Math.min(1000, Math.max(0, Math.round(((event.clientX - rect.left) / rect.width) * 1000))),
      y: Math.min(1000, Math.max(0, Math.round(((event.clientY - rect.top) / rect.height) * 1000))),
    };
  }
}
