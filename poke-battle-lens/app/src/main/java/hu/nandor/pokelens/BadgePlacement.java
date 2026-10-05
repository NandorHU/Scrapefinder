package hu.nandor.pokelens;
import java.util.*;

/** Places labels next to names, rejecting collisions with text and other badges. */
final class BadgePlacement {
    static final class Box {
        final float left,top,right,bottom;
        Box(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}
        boolean intersects(Box o){return left<o.right&&right>o.left&&top<o.bottom&&bottom>o.top;}
    }
    static Box place(MovePosition move,float badgeW,float badgeH,int width,int height,float gap,List<MovePosition> names,List<Box> occupied){
        float l=move.left*width,t=move.top*height,r=move.right*width,b=move.bottom*height,cy=(t+b-badgeH)/2;
        float[][] candidates={{r+gap,cy},{l-gap-badgeW,cy},{r-badgeW,b+gap},{l,b+gap},{r-badgeW,t-gap-badgeH},{l,t-gap-badgeH}};
        for(float[] p:candidates){
            Box box=new Box(p[0],p[1],p[0]+badgeW,p[1]+badgeH);if(box.left<0||box.top<0||box.right>width||box.bottom>height)continue;
            boolean collision=false;
            for(MovePosition n:names)if(box.intersects(new Box(n.left*width-gap/2,n.top*height-gap/2,n.right*width+gap/2,n.bottom*height+gap/2))){collision=true;break;}
            if(!collision)for(Box used:occupied)if(box.intersects(used)){collision=true;break;}
            if(!collision)return box;
        }
        return null; // No safe space: never cover the attack name with a guessed position.
    }
}
