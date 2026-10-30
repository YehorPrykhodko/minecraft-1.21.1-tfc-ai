package fr.ypwildlife.cerveau;
public record Position(double x, double y, double z) {
    public double distance(Position p) { return Math.sqrt(Math.pow(x-p.x,2)+Math.pow(y-p.y,2)+Math.pow(z-p.z,2)); }
    public Position vers(Position p, double longueur) { double d=distance(p); return d<0.001?this:new Position(x+(p.x-x)*longueur/d,y,z+(p.z-z)*longueur/d); }
}
