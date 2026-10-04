package hu.nandor.pokelens;

final class BadgeValue {
    final String label;final double factor;final boolean status,unknown;
    BadgeValue(String label,double factor,boolean status,boolean unknown){this.label=label;this.factor=factor;this.status=status;this.unknown=unknown;}
    boolean exceptional(){return unknown||(!status&&factor!=1);}
    static BadgeValue of(Dex dex,String name,String enemy,int generation){
        Dex.Move move=name==null?null:dex.move(name,generation);
        if(move==null)return new BadgeValue("?",1,false,true);
        if(move.category==1)return new BadgeValue("áll.",1,true,false);
        int[] types=dex.types(enemy,generation);if(types.length==0)return new BadgeValue("?",1,false,true);
        double factor=BattleMath.effectiveness(move.type,types,generation,dex.chart);
        return new BadgeValue(BattleSummary.multiplier(factor)+"×",factor,false,false);
    }
}
