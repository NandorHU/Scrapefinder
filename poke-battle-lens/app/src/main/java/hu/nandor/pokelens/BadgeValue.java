package hu.nandor.pokelens;

final class BadgeValue {
    final String label,accuracy;final double factor;final boolean status,unknown,recommended;
    BadgeValue(String label,String accuracy,double factor,boolean status,boolean unknown){this(label,accuracy,factor,status,unknown,false);}
    private BadgeValue(String label,String accuracy,double factor,boolean status,boolean unknown,boolean recommended){this.label=label;this.accuracy=accuracy;this.factor=factor;this.status=status;this.unknown=unknown;this.recommended=recommended;}
    BadgeValue withRecommendation(){return new BadgeValue(label+" ★",accuracy,factor,status,unknown,true);}
    boolean exceptional(){return unknown||(!status&&factor!=1);}
    static String accuracy(Dex.Move move){return move.accuracy>0?move.accuracy+"%":"—";}
    /** Accuracy remains visible when neutral/status multipliers are collapsed. */
    String text(boolean all){return all||exceptional()||recommended?label+(accuracy.isEmpty()?"":"\n"+accuracy):accuracy;}
    static BadgeValue of(Dex dex,String name,String enemy,int generation){
        Dex.Move move=name==null?null:dex.move(name,generation);
        if(move==null)return new BadgeValue("?","",1,false,true);
        String accuracy=accuracy(move);
        if(move.category==1)return new BadgeValue("áll.",accuracy,1,true,false);
        int[] types=dex.types(enemy,generation);if(types.length==0)return new BadgeValue("?",accuracy,1,false,true);
        double factor=BattleMath.effectiveness(move.type,types,generation,dex.chart);
        return new BadgeValue(BattleSummary.multiplier(factor)+"×",accuracy,factor,false,false);
    }
}
