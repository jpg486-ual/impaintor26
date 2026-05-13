import { Injectable, PLATFORM_ID, inject, RendererFactory2, Renderer2 } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

@Injectable({
  providedIn: 'root'
})
export class AudioService {
  private readonly platformId = inject(PLATFORM_ID);
  
  private audioA: HTMLAudioElement | null = null;
  private audioB: HTMLAudioElement | null = null;
  private currentAudio: HTMLAudioElement | null = null;
  
  private currentTrack: string | null = null;
  private pendingTrack: string | null = null;
  private hasInteracted = false;
  private fadeInterval: any = null;

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      this.audioA = new Audio();
      this.audioB = new Audio();
      this.audioA.loop = this.audioB.loop = true;
      this.audioA.volume = this.audioB.volume = 0;
      this.currentAudio = this.audioA;

      const unlockAudio = () => {
        if (this.hasInteracted) return;
        this.hasInteracted = true;
        if (this.pendingTrack) {
          this.play(this.pendingTrack);
          this.pendingTrack = null;
        }
        document.removeEventListener('click', unlockAudio);
        document.removeEventListener('keydown', unlockAudio);
      };

      document.addEventListener('click', unlockAudio);
      document.addEventListener('keydown', unlockAudio);
    }
  }

  play(trackPath: string, crossFadeDuration: number = 1000): void {
    if (!isPlatformBrowser(this.platformId) || !this.audioA || !this.audioB) return;
    
    if (!this.hasInteracted) {
      this.pendingTrack = trackPath;
      return;
    }

    if (this.currentTrack === trackPath) return;

    const nextAudio = this.currentAudio === this.audioA ? this.audioB : this.audioA;
    const prevAudio = this.currentAudio;

    this.currentTrack = trackPath;
    nextAudio.src = trackPath;
    nextAudio.load();
    nextAudio.play().then(() => {
      this.fadeOutIn(prevAudio!, nextAudio, crossFadeDuration);
    }).catch(err => {
      console.warn('Audio play failed:', err);
    });

    this.currentAudio = nextAudio;
  }

  private fadeOutIn(outAudio: HTMLAudioElement, inAudio: HTMLAudioElement, duration: number): void {
    if (this.fadeInterval) clearInterval(this.fadeInterval);

    const steps = 20;
    const stepDuration = duration / steps;
    const volumeStep = 0.5 / steps; // Target volume 0.5

    inAudio.volume = 0;
    
    this.fadeInterval = setInterval(() => {
      let finished = true;

      // Fade out
      if (outAudio.volume > 0) {
        outAudio.volume = Math.max(0, outAudio.volume - volumeStep);
        finished = false;
      } else {
        outAudio.pause();
      }

      // Fade in
      if (inAudio.volume < 0.5) {
        inAudio.volume = Math.min(0.5, inAudio.volume + volumeStep);
        finished = false;
      }

      if (finished) {
        clearInterval(this.fadeInterval);
        this.fadeInterval = null;
      }
    }, stepDuration);
  }

  stop(duration: number = 500): void {
    if (!isPlatformBrowser(this.platformId) || !this.currentAudio) return;
    
    if (this.fadeInterval) clearInterval(this.fadeInterval);
    
    const steps = 10;
    const stepDuration = duration / steps;
    const volumeStep = this.currentAudio.volume / steps;

    this.fadeInterval = setInterval(() => {
      if (this.currentAudio && this.currentAudio.volume > 0) {
        this.currentAudio.volume = Math.max(0, this.currentAudio.volume - volumeStep);
      } else {
        clearInterval(this.fadeInterval);
        this.fadeInterval = null;
        if (this.currentAudio) {
          this.currentAudio.pause();
          this.currentAudio.currentTime = 0;
        }
        this.currentTrack = null;
        this.pendingTrack = null;
      }
    }, stepDuration);
  }
}
