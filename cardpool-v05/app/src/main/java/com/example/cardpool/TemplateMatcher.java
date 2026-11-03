package com.example.cardpool;
/** Local RGB similarity score. It is not a calibrated statistical probability. */
public final class TemplateMatcher {
 public static double score(int[] a,int[] b){if(a.length!=b.length||a.length<4)return 0;
  double error=0,variance=0,mean=0;for(int p:b)mean+=((p>>16)&255)+((p>>8)&255)+(p&255);mean/=b.length*3;
  for(int i=0;i<a.length;i++)for(int shift:new int[]{16,8,0}){int x=(a[i]>>shift)&255,y=(b[i]>>shift)&255;error+=Math.abs(x-y);variance+=(y-mean)*(y-mean);}
  if(variance/(b.length*3)<100)return 0;return Math.max(0,1-error/(b.length*3*255));
 }
}
