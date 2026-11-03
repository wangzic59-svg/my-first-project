package com.example.cardpool;
/** Bound capture allocation, retaining normalized ROI aspect ratio on high-DPI devices. */
public final class CaptureDimensions {
 public static int[] fit(int width,int height){
  if(width<=0||height<=0)throw new IllegalArgumentException("Invalid capture dimensions");
  double scale=Math.min(1.,1920./Math.max(width,height));
  return new int[]{Math.max(1,(int)Math.round(width*scale)),Math.max(1,(int)Math.round(height*scale))};
 }
}
