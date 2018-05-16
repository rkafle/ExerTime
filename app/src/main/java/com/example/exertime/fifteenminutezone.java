package com.example.exertime;
import java.util.ArrayList;

/**
 * Created by robertclark on 4/17/18.
 */

public class fifteenminutezone {
    ArrayList<OurEvent> listofevents = new ArrayList<OurEvent>();
    public int timeofbeginning;
    public boolean isthereanexercise;
    public boolean isthereaneventhere;
    public Exercise exerewise;

    /**fifteenminutezone
     * sets up a fifteenminutezone object.
     * @param timestart
     * @param exercisehere
     * @param eventhere
     * @param g
     */
    public  fifteenminutezone(int timestart, boolean exercisehere, boolean eventhere, Exercise g){
        timeofbeginning = timestart;
        isthereanexercise = exercisehere;
        isthereaneventhere = eventhere;
        exerewise = g;

    }


    public void exercisepresent(boolean r){
        isthereanexercise  = r;
    }

    public boolean isthereanexercisehere(){
        return   isthereanexercise;
    }

    /** Method that determines whether or not there is a an event at a fifteenminute time
     * isthereanevent()
     * @return isthereanevent
     */
    public boolean isthereanevent(){
        return  isthereaneventhere;
    }

    /** getExercise()
     *
     * @return exercise
     */
    public Exercise getExercise(){
        return  exerewise;
    }

    public void setexercise(Exercise r){
        exerewise  = r;
    }


    public void setevent(boolean r){
        isthereaneventhere  = r;
    }
}
