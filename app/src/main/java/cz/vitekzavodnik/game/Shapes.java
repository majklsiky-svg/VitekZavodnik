package cz.vitekzavodnik.game;

import java.util.ArrayList;
import java.util.List;

public final class Shapes {
    private Shapes() {}

    public static Mesh cube() {
        List<Float> v = new ArrayList<>();
        face(v, 0,0,1,  -0.5f,-0.5f,0.5f,   0.5f,-0.5f,0.5f,   0.5f,0.5f,0.5f,   -0.5f,0.5f,0.5f);
        face(v, 0,0,-1, 0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,0.5f,-0.5f);
        face(v, 1,0,0,  0.5f,-0.5f,0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f);
        face(v, -1,0,0, -0.5f,-0.5f,-0.5f, -0.5f,-0.5f,0.5f, -0.5f,0.5f,0.5f, -0.5f,0.5f,-0.5f);
        face(v, 0,1,0,  -0.5f,0.5f,0.5f, 0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f);
        face(v, 0,-1,0, -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,0.5f);
        return new Mesh(toArray(v));
    }

    private static void face(List<Float> out, float nx, float ny, float nz,
                             float ax,float ay,float az, float bx,float by,float bz,
                             float cx,float cy,float cz, float dx,float dy,float dz) {
        vert(out,ax,ay,az,nx,ny,nz); vert(out,bx,by,bz,nx,ny,nz); vert(out,cx,cy,cz,nx,ny,nz);
        vert(out,ax,ay,az,nx,ny,nz); vert(out,cx,cy,cz,nx,ny,nz); vert(out,dx,dy,dz,nx,ny,nz);
    }

    public static Mesh sphere(int stacks, int slices) {
        List<Float> out = new ArrayList<>();
        for (int i=0;i<stacks;i++) {
            double p0 = -Math.PI/2.0 + Math.PI * i / stacks;
            double p1 = -Math.PI/2.0 + Math.PI * (i+1) / stacks;
            for (int j=0;j<slices;j++) {
                double t0 = 2*Math.PI * j / slices;
                double t1 = 2*Math.PI * (j+1) / slices;
                float[] a = sp(p0,t0), b = sp(p0,t1), c = sp(p1,t1), d = sp(p1,t0);
                addPN(out,a); addPN(out,b); addPN(out,c);
                addPN(out,a); addPN(out,c); addPN(out,d);
            }
        }
        return new Mesh(toArray(out));
    }

    private static float[] sp(double p, double t) {
        float x=(float)(Math.cos(p)*Math.sin(t));
        float y=(float)Math.sin(p);
        float z=(float)(Math.cos(p)*Math.cos(t));
        return new float[]{x*0.5f,y*0.5f,z*0.5f,x,y,z};
    }

    private static void addPN(List<Float> out,float[] p){ for(float f:p) out.add(f); }

    public static Mesh cylinder(int segments) {
        List<Float> out = new ArrayList<>();
        float r=0.5f;
        for(int i=0;i<segments;i++){
            double a0=2*Math.PI*i/segments, a1=2*Math.PI*(i+1)/segments;
            float x0=(float)Math.sin(a0)*r, z0=(float)Math.cos(a0)*r;
            float x1=(float)Math.sin(a1)*r, z1=(float)Math.cos(a1)*r;
            float nx0=x0/r, nz0=z0/r, nx1=x1/r, nz1=z1/r;
            vert(out,x0,-0.5f,z0,nx0,0,nz0); vert(out,x1,-0.5f,z1,nx1,0,nz1); vert(out,x1,0.5f,z1,nx1,0,nz1);
            vert(out,x0,-0.5f,z0,nx0,0,nz0); vert(out,x1,0.5f,z1,nx1,0,nz1); vert(out,x0,0.5f,z0,nx0,0,nz0);
            vert(out,0,0.5f,0,0,1,0); vert(out,x0,0.5f,z0,0,1,0); vert(out,x1,0.5f,z1,0,1,0);
            vert(out,0,-0.5f,0,0,-1,0); vert(out,x1,-0.5f,z1,0,-1,0); vert(out,x0,-0.5f,z0,0,-1,0);
        }
        return new Mesh(toArray(out));
    }

    public static Mesh pyramid() {
        List<Float> out = new ArrayList<>();
        float[][] p={{-0.5f,0,-0.5f},{0.5f,0,-0.5f},{0.5f,0,0.5f},{-0.5f,0,0.5f},{0,1,0}};
        triAuto(out,p[0],p[1],p[4]); triAuto(out,p[1],p[2],p[4]); triAuto(out,p[2],p[3],p[4]); triAuto(out,p[3],p[0],p[4]);
        triAuto(out,p[0],p[3],p[2]); triAuto(out,p[0],p[2],p[1]);
        return new Mesh(toArray(out));
    }

    private static void triAuto(List<Float> out,float[] a,float[] b,float[] c){
        float ux=b[0]-a[0], uy=b[1]-a[1], uz=b[2]-a[2];
        float vx=c[0]-a[0], vy=c[1]-a[1], vz=c[2]-a[2];
        float nx=uy*vz-uz*vy, ny=uz*vx-ux*vz, nz=ux*vy-uy*vx;
        float l=(float)Math.sqrt(nx*nx+ny*ny+nz*nz); if(l<1e-6f)l=1;
        nx/=l; ny/=l; nz/=l;
        vert(out,a[0],a[1],a[2],nx,ny,nz); vert(out,b[0],b[1],b[2],nx,ny,nz); vert(out,c[0],c[1],c[2],nx,ny,nz);
    }

    private static void vert(List<Float> out,float x,float y,float z,float nx,float ny,float nz){
        out.add(x);out.add(y);out.add(z);out.add(nx);out.add(ny);out.add(nz);
    }
    private static float[] toArray(List<Float> l){
        float[] a=new float[l.size()]; for(int i=0;i<a.length;i++)a[i]=l.get(i); return a;
    }
}
