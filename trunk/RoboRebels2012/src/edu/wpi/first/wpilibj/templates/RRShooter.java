/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.wpi.first.wpilibj.templates;

import com.sun.squawk.util.MathUtils;
import edu.wpi.first.wpilibj.*;

/**
 *
 * The shooter class has the following components:
 * 
 * - Shooting wheel
 * - Getting/setting shooting wheel speed
 * - Tilting mechanism
 * - Lazy Susan
 * 
 * Currently, all manual control has been developed for
 * controlling the various parts of the shooter module.  
 * 
 * TODO:
 * 
 * - Test!
 * - Implement accelerometer code
 * - Extend public methods for autonomous control
 * 
 * @author dmw
 */
public class RRShooter 
{
    private final double        MAX_SHOOTING_SPEED = 1.0;
    private final double        STARTING_SHOOTING_SPEED = 0.85;
    private final double        LS_SPEED = 0.20;
    private final double        TILT_SPEED = 0.4;
    private final double        EXP_CONTR_TILT_MULT = 0.75;
    private final double        EXP_CONT_MAX_ANGLE = 85,
                                EXP_CONT_MIN_ANGLE = 60;
    
    private     int             swj_channel;        // Shooter Wheel Jaguar channel
    private     int             lsv_channel;        // Lazy Susan Victor channel
    private     int             tltv_channel;       // Tilter Victor channel
    private     int             tltls_channel;      // Tilter Limit Swith channel
    private     int             os_top_channel;     // Top Optical Sensor channel
    private     int             os_bottom_channel;  // Bottom Optical Sensor channel

    private     double          shootingWheelSpeed = 0.0;
    private     double          lazySusanSpeed = 0.0;
    private     double          current_lazySusanSpeed = 0.0;
    private     double          tiltSpeed = 0.0;
    
    private     boolean         shootingWheelState;
    
    private     boolean         shootingButtonPressed;
    
    private     boolean         retractionContractionInProgress = false;
            
    private     Jaguar          shootingWheelJaguar;
    private     Victor          tiltVictor;
    private     Victor          lsVictor;
    private     DigitalInput    tiltLimitSwitch;
    private     DigitalInput    topOpticalSensor;
    private     DigitalInput    bottomOpticalSensor;
    
    private     Joystick        shootingJoystick;
    
    private     RRTracker       tracker;
    private     RRBallSensor    sensor;
    private     RRGatherer      gatherer;
    
    private     boolean         tracking = false; // Indicates if robot is tracking target
    //private     boolean         elevation_tracking = false;  // I don't think we need separate tracking
                                                               // for azimuth and elevation.
    
    private boolean isRetracting = false;
    private boolean isExpanding = false;


    private boolean ball_present = false;
        
    //int target_direction = 1;
    
    /**
     * Sets up the speed controllers for the shooter
     * @param swjc Shooting Wheel Jaguar Channel
     * @param lsvc Lazy Susan Victor Channel
     * @param tltvc Tilter Victor Channel 
     * @param tltlsc Tilter Limit Switch Channel
     * @param js Joystick to monitor for button/axis events
     */
    public RRShooter(int  swjc, int lsvc, int tltvc, int tltlsc, RRTracker t, RRBallSensor ballSensor)
    {
        swj_channel = swjc;
        lsv_channel = lsvc;
        tltv_channel = tltvc;
        tltls_channel = tltlsc;
        
        sensor = ballSensor;
        
        shootingWheelState = false;         // start with the shooting wheel off!
        
        shootingButtonPressed = false;      // indicates if the 
        
        
        
        if ( t != null )
            tracker = t;
        else
            throw new NullPointerException("RRShooter was passed a null RRTracker object!");
        
        shootingWheelJaguar = new Jaguar(swj_channel);
        tiltVictor = new Victor(tltv_channel);
        lsVictor = new Victor(lsv_channel);
        //tiltLimitSwitch = new DigitalInput(tltls_channel);
    }
    
    
    /**
     * 
     * @param distance distance is distance to basket in feet
     * @param targetID targetID indicates if lower (0), middle (1), or upper (2) is the target
     * @return Returns the angle for which to move the shooter at
     */
    
    static double determineAngle(double distance, double muzzleVelocity, int targetID)
    {            
//      double muzzleVelocity = 8.1; //meters per second
//      double muzzleVelocity = 7.1; //meters per second
   
        double gravity = 9.81;  //meters per (second)^2
        double yLower = 0.7112;         // .466953;
        double yMiddle = 1.5494;       // 1.30515;
        double yHigher = 2.4892;        //2.24485;
        double xDistance = 3.6576; // distance to base of basket (as if shooting from key)
        double shooterHeight = .914; // meters off the ground
        double y = yHigher; // determined by targetID (see below)
        double theta = 0;
                
        xDistance = distance / 3.28; // converts feet into meters
        
        if (targetID == RoboRebels.LOWEST_TARGET)
            y = yLower; 
        else if ((targetID == RoboRebels.LEFT_TARGET) && (targetID == RoboRebels.RIGHT_TARGET))
             y = yMiddle;
        else if (targetID == RoboRebels.HIGHEST_TARGET)
             y = yHigher;
        else if (targetID == RoboRebels.AUTO_TARGET)
        {
            if (RoboRebels.going_for_highest)
                y = yHigher;
            else
                y = yLower;
        }
        
        y -= Math.max(shooterHeight,0);
                 
        System.out.println("d: " + RRTracker.round(distance) + "v: " + RRTracker.round(muzzleVelocity) +
                "y: " + y + "x: " + xDistance);

        double tempSqrtEquation = (muzzleVelocity*muzzleVelocity*muzzleVelocity*muzzleVelocity)-
                         (2*gravity*muzzleVelocity*muzzleVelocity*y)-(gravity*gravity*xDistance*xDistance);
        
        if (tempSqrtEquation > 0)
        {
       //     System.out.println("tempSqrtEq: " + RRTracker.round2(tempSqrtEquation));
            theta = MathUtils.atan(((muzzleVelocity*muzzleVelocity)+(Math.sqrt(tempSqrtEquation)))/(gravity*xDistance));
        }
        else
            theta = 0;    // There is no angle for this muzzle velocity
        
        theta = theta * ( 180.0 / 3.14159265); // converts radians to degreese
        
        //theta -= 10;    // Fudge factor to make theta correct.

        return theta;
    }
    
    
    /**
     * Gathers input states and sets up the necessary motor speeds
     */
    private void gatherInputStates()
    {
        double retExpAngle = 0.0;
        boolean LSLState, LSRState, TUState, TDState, TTState, CSState, ESState, ECSState, ReverseShootingState1, ReverseShootingState2;
        boolean  shooterButtonState = RRButtonMap.getActionObject(RRButtonMap.SHOOTER_ENABLED).getButtonState();
        
        RoboRebels.printLCD(3, "SS: " + RRTracker.round2(shootingWheelJaguar.get()) + " Z: " + RRTracker.round2(this.getTransformedZValue()));
        //RoboRebels.printLCD(4, "Z:" + RRTracker.round2(this.getTransformedZValue()));
        System.out.println("Shooting Speed: " + RRTracker.round2(shootingWheelJaguar.get()));
        System.out.println("Z: " + RRTracker.round2(this.getTransformedZValue()));
        //System.out.println("Limit Switch: " + tiltLimitSwitch.get());
        
        
        // Spin up if trigger is pressed 
        //if ( shootingJoystick.getRawButton(RRButtonMap.SHOOT) && !shootingButtonPressed )
        if ( shooterButtonState && !shootingButtonPressed )
        {
            System.out.println("Trigger");
            shootingWheelState = !shootingWheelState;
        }
        //else if ( !shootingJoystick.getRawButton(RRButtonMap.SHOOT) )
        else if ( !shooterButtonState )
        {
            shootingButtonPressed = false;
        }
        
        if ( shootingWheelState ) // Shooting button just pressed
        {
        //    shootingWheelSpeed = this.getTransformedZValue();
            
            // Set shooter wheel to speed set by tracking calculations
            
            if (RoboRebels.muzzle_velocity == 7.5)
                shootingWheelSpeed = 0.85;
            else if (RoboRebels.muzzle_velocity == 8.0)
                shootingWheelSpeed = 1.0;
            else
                shootingWheelSpeed = 0.85;
           
            shootBall();
            
        } else 
        {
            shootingWheelSpeed = 0.0;
        }
        
        
        // Check for tilting button up, down 
        //if ( shootingJoystick.getRawButton(RRButtonMap.TILT_UP) )
        TUState = RRButtonMap.getActionObject(RRButtonMap.TILT_UP).getButtonState();
        TDState = RRButtonMap.getActionObject(RRButtonMap.TILT_DOWN).getButtonState();
        if ( TUState )
        {
            System.out.println("Tilt up");
            RoboRebels.elevation_lock = false;         // No elevation target lock
 
            tiltSpeed = -1.0 * TILT_SPEED;
            
        }
        else if ( TDState )
        {
            if (RoboRebels.tilt_angle > RoboRebels.MIN_TILT_ANGLE)   // Check to make sure accel tilt angle isn't too low
            {        
                System.out.println("Tilt down");

                /*if (!tiltLimitSwitch.get())
                {
                    tiltSpeed = TILT_SPEED;
                } else {
                    System.out.println("Tilter limit switch pressed!");
                    tiltSpeed = 0.0;
                }*/
                RoboRebels.elevation_lock = false;         // No elevation target lock
 
                tiltSpeed = TILT_SPEED;
            }
            else
            {
                System.out.println("Min tilt angle exceeded!");
                tiltSpeed = 0.0;              
            }
        }
        else if ( !TUState && !TDState )
        {
            tiltSpeed = 0.0;
        }
        
        
        LSLState = RRButtonMap.getActionObject(RRButtonMap.LAZY_SUSAN_LEFT).getButtonState();
        LSRState = RRButtonMap.getActionObject(RRButtonMap.LAZY_SUSAN_RIGHT).getButtonState();
        
        // Check for lazy susan button left, right 
        //if ( shootingJoystick.getRawButton(RRButtonMap.LAZY_SUSAN_LEFT) )
        if ( LSLState )
        {
           System.out.println("Lazy susan Right");
           RoboRebels.azimuth_lock = false;         // No azimuth target lock
           if (LS_SPEED == 0.2)
                lazySusanSpeed = 1.0 * LS_SPEED * 1.2;  // Motor runs more slowly to left at this speed;; 
            else
                lazySusanSpeed = 1.0 * LS_SPEED;

        }
        else if ( LSRState )
        {
            RoboRebels.azimuth_lock = false;         // No azimuth target lock
            lazySusanSpeed = -1.0 * LS_SPEED;
            System.out.println("Lazy susan Left"); 
         }
        else if ( !LSLState && !LSRState )
        {
            lazySusanSpeed = 0.0;
        }
        
     
        
        TTState = RRButtonMap.getActionObject(RRButtonMap.TRACK_TARGET).getButtonState();
        //if (shootingJoystick.getRawButton(RRButtonMap.TRACK_TARGET))  // Target Tracking when joystick button 11 is pressed and held 
        if ( TTState  || RoboRebels.autonomous_mode)
        {
             if (TTState) 
                 System.out.println("Track Target Button Pressed");
                        
            if (RoboRebels.target_azimuth == RoboRebels.CLOSE_LEFT)   // Left slow
            {
                System.out.println("Auto Lazy susan left fast"); 
                lazySusanSpeed = -0.15;  
                tracking = true;
                RoboRebels.azimuth_lock = false;         // No azimuth target lock
            }
            else if (RoboRebels.target_azimuth == RoboRebels.LEFT)       // Left normal
            {
                System.out.println("Auto Lazy susan left"); 
                lazySusanSpeed = -0.20;  // was -0.75 * LS_SPEED
                tracking = true;
                RoboRebels.azimuth_lock = false;         // No azimuth target lock
            }
            else if (RoboRebels.target_azimuth == RoboRebels.FAR_LEFT)   // Left fast
            {
                System.out.println("Auto Lazy susan left fast"); 
                lazySusanSpeed = -0.3;  
                tracking = true;
                RoboRebels.azimuth_lock = false;         // No azimuth target lock
            }
            else if (RoboRebels.target_azimuth == RoboRebels.CLOSE_RIGHT)     // Right slowly
            {
                System.out.println("Auto Lazy susan right"); 
                lazySusanSpeed = 0.15 * 1.2;            // Added 20% due to motor slowness
                tracking = true;
                RoboRebels.azimuth_lock = false;         // No azimuth target lock
            }
            else if (RoboRebels.target_azimuth == RoboRebels.RIGHT)     // Right normal
            {
                System.out.println("Auto Lazy susan right"); 
                lazySusanSpeed = 0.20  * 1.2;           // Added 20% due to motor slowness
                tracking = true;
                RoboRebels.azimuth_lock = false;         // No azimuth target lock
            }
            else if (RoboRebels.target_azimuth == RoboRebels.FAR_RIGHT)  // Right fast
            {
                System.out.println("Auto Lazy susan right fast"); 
                lazySusanSpeed = 0.30;
                tracking = true;
                RoboRebels.azimuth_lock = false;         // No azimuth target lock
            }
            else if (RoboRebels.target_azimuth == RoboRebels.LOCK)
            {
                System.out.println("Auto Lazy susan Lock"); 
                lazySusanSpeed = 0.0;
                RoboRebels.azimuth_lock = true;         // Indicate azimuth target lock
                tracking = true;
            } 
            else              // Must be set to HOLD
            {
                System.out.println("Auto Lazy susan Hold"); 
                lazySusanSpeed = 0.0;
                RoboRebels.azimuth_lock = false;         // No azimuth target lock
                tracking = true;
            }
             
            System.out.println("Tilt value:" + RoboRebels.target_elevation); 
           
            if (RoboRebels.target_elevation == RoboRebels.UP)  // Track target elevation (up/down)
            {
                System.out.println("Auto Tilt Up"); 
                tiltSpeed = -1.0 * TILT_SPEED * 0.5;
                RoboRebels.elevation_lock = false;         // No elevation target lock
            }
            else if (RoboRebels.target_elevation == RoboRebels.DOWN)
            {
                System.out.println("Auto Tilt Down"); 
                tiltSpeed = 1.0 * TILT_SPEED * 0.5;
                RoboRebels.elevation_lock = false;         // No elevation target lock
           }
           else if (RoboRebels.target_elevation == RoboRebels.LOCK)
           {
                tiltSpeed = 0.0;                      // Stop Tilting
                RoboRebels.elevation_lock = true;     // Indicate elevation target lock
                System.out.println("Auto Tilt Lock"); 
            }
            else             // is HOLD
            {
               tiltSpeed = 0.0;
               RoboRebels.elevation_lock = false;         // No elevation target lock
               System.out.println("Auto Tilt Hold"); 
            }
            
            if (RoboRebels.target_muzzle_velocity == RoboRebels.FASTER)
            {
                System.out.println("Auto Shooting Speed Up"); 
                RoboRebels.muzzle_velocity = 8.0;
                RoboRebels.muzzle_velocity_lock = false;         // No muzzle velocity target lock
            }
            else if (RoboRebels.target_muzzle_velocity == RoboRebels.SLOWER)
            {
                System.out.println("Auto Shooting Speed Down"); 
                RoboRebels.muzzle_velocity = 7.5;
                RoboRebels.muzzle_velocity_lock = false;         // No muzzle velocity target lock
            }
            else if (RoboRebels.target_muzzle_velocity == RoboRebels.LOCK)
            {
                // Muzzle velocity is fine - don't change
                RoboRebels.muzzle_velocity_lock = true;     // muzzle velocity target lock
                System.out.println("Auto Shooting Lock"); 
            }
            else
            {
                  RoboRebels.muzzle_velocity_lock = false;         // No muzzle velocity target lock
                  System.out.println("Auto Shooting Hold"); 
            }
        
        } else if (tracking)   // Was tracking target, but now no longer tracking
        {
             lazySusanSpeed = 0.0;      // Stop azimuth tracking
             tiltSpeed = 0.0;           // Stop elevation tracking
                                        // Stop shooter speed tracking
             tracking = false;
             RoboRebels.target_azimuth = RoboRebels.HOLD;   //  Put hold on tracking until tracking is re-initiated
             RoboRebels.target_elevation = RoboRebels.HOLD;
             RoboRebels.target_muzzle_velocity = RoboRebels.HOLD;
        }
        
        System.out.println("Target_Azimuth:" + RRTracker.round(RoboRebels.target_azimuth) + " Targeting: " +
                tracking + " lazySusanSpeed: " + RRTracker.round2(lazySusanSpeed) + 
                " tiltSpeed: " + RRTracker.round2(tiltSpeed));
        
        
        ECSState = RRButtonMap.getActionObject(RRButtonMap.EXPAND_CONTRACT_SHOOTER).getButtonState();
        //CSState = RRButtonMap.getActionObject(RRButtonMap.CONTRACT_SHOOTER).getButtonState();
        
        if ( ECSState )
        {
            retExpAngle = tracker.accelAngle();
            if (retExpAngle >= EXP_CONT_MAX_ANGLE) {
                if (!isRetracting ) {
                    System.out.println("##### Shooter will expand now! " + retExpAngle);
                    isExpanding = true;
                }
            }
            else if (retExpAngle < EXP_CONT_MAX_ANGLE) {
                if (!isExpanding) {
                    System.out.println("##### Shooter will contract now" + retExpAngle );
                    isRetracting = true;
                }
            }
        }
        
        
        if (isRetracting) {
            retExpAngle = tracker.accelAngle();
            if (retExpAngle < EXP_CONT_MAX_ANGLE) {
                System.out.println("##### Shooter is retracting! " + retExpAngle);
                tiltSpeed = -1 * TILT_SPEED * EXP_CONTR_TILT_MULT;
            }
            else if (retExpAngle >= EXP_CONT_MAX_ANGLE) {
                tiltSpeed = 0;
                isRetracting = false;
            }
        }
        if (isExpanding) {
            retExpAngle = tracker.accelAngle();
            if (retExpAngle >= EXP_CONT_MIN_ANGLE) {
                System.out.println("##### Shooter is expanding! " + retExpAngle);
                tiltSpeed = TILT_SPEED * EXP_CONTR_TILT_MULT;
            }
            else if (retExpAngle < EXP_CONT_MIN_ANGLE)
            {
                tiltSpeed = 0;
                isExpanding = false;
            }
        }
        ReverseShootingState1 = RRButtonMap.getActionObject(RRButtonMap.REVERSE_SHOOTING_1).getButtonState();
        ReverseShootingState2 = RRButtonMap.getActionObject(RRButtonMap.REVERSE_SHOOTING_2).getButtonState();
        if ((ReverseShootingState1) && (ReverseShootingState2)) {
            shootingWheelState = false;
            shootingWheelSpeed = -this.getTransformedZValue();
        }
        

       if (RoboRebels.azimuth_lock && RoboRebels.elevation_lock && RoboRebels.muzzle_velocity_lock)
       {
            RoboRebels.printLCD(6, "All Locked!                ");  
            shootBall();
       }
       else if (RoboRebels.azimuth_lock && RoboRebels.muzzle_velocity_lock)
            RoboRebels.printLCD(6, "LS & Speed Locked!          "); 
       else if (RoboRebels.azimuth_lock && RoboRebels.elevation_lock)
            RoboRebels.printLCD(6, "LS & Angle Locked!          "); 
       else if (RoboRebels.elevation_lock && RoboRebels.muzzle_velocity_lock)
            RoboRebels.printLCD(6, "Angle & Speed Locked!       "); 
       else if (RoboRebels.elevation_lock)
            RoboRebels.printLCD(6, "Angle Locked!               ");
       else if (RoboRebels.muzzle_velocity_lock)
            RoboRebels.printLCD(6, "Speed Locked!               ");
       else if (RoboRebels.azimuth_lock)
            RoboRebels.printLCD(6, "LS Locked!                  ");
       else if (tracking) 
            RoboRebels.printLCD(6, "Tracking Target...          "); 
       else 
            RoboRebels.printLCD(6, "                            ");   // Clear the display
    }
    
   
    
    /**
     * This is the general shoot periodic method for shooting functions.
     */
    
    public void shoot()
    {
        // Process input from joystick and other inputs
        System.out.println("Starting Shoot"); 
        
        gatherInputStates();
        
        System.out.println("Middle of Shoot");
        
        if (RoboRebels.isShooting){
            
             
            System.out.println("Ball Sensor: " + sensor.getShootSensor());  // True if ball is there, false if no ball
        
            if (sensor.getShootSensor() == true)
            {
                ball_present = true; 
            }

            else if (sensor.getShootSensor() == false && ball_present == true )

            {
                // load motor stop
                
                //gatherer.stop();
                System.out.println("Gatherer Motor is Off!");

                ball_present = false;
                RoboRebels.isShooting = false;
                
                RoboRebels.isFinishedShooting = true;   // Need to set this to false somewhere
           
            }       
        
            // gatherer.elevate();
            System.out.println("Gatherer Motor is On!");

            
        }
        
        // Process shooter states
        setShooterSpeeds();
  
    }
    
    
    /**
     * This private method sets the determined speeds for the various
     * mechanisms used by the shooter module.  
     */
    
    private void setShooterSpeeds()
    {
        shootingWheelJaguar.set(-1.0 * shootingWheelSpeed);
        System.out.println("s: " + RRTracker.round2(tiltSpeed));
        tiltVictor.set(tiltSpeed);
        
 //       int result = check_ls_position();    // Check for LS position before changing LS Speed.
        
 //       if ((lazySusanSpeed < 0.0) && result == RoboRebels.AT_LEFT_LIMIT)
 //       {
 //           System.out.println("LS Stopped at Left Limit");
            //lazySusanSpeed = 0.0;
  //      }
  //      else if ((lazySusanSpeed > 0.0) && result == RoboRebels.AT_RIGHT_LIMIT)
  //      {
   //         System.out.println("LS Stopped at Right Limit");
            //lazySusanSpeed = 0.0;
   //     }   
        lsVictor.set(lazySusanSpeed);
        current_lazySusanSpeed = lazySusanSpeed;    // Stores current speed for next position calculation
    }
    
    /**
     * This method gets the value of the Z dial on a joystick and
     * transforms it to fit within the speed range of the shooter.
     * 
     * @return transformed speed from the corresponding Z value
     */
    
    private double getTransformedZValue()
    {
        double speed;
        RRAction        aoS = RRButtonMap.getActionObject(RRButtonMap.SHOOTER_SPEED);
 //       return MAX_SHOOTING_SPEED * (aoS.js.getZ() + 1.0) / 2.0;
        
        //  speed is adjusted between (2*STARTING_SHOOTING_SPEED - MAX_SHOOTING_SPEED) and MAX_SHOOTING_SPEED
        
        speed = STARTING_SHOOTING_SPEED + (STARTING_SHOOTING_SPEED - MAX_SHOOTING_SPEED) * aoS.js.getZ(); // getZ() is in range +/- 1?
        System.out.println("New Shooting Speed: " + RRTracker.round2(speed));
        return speed;
    }
    
        /**
     * This public immediately stops the lazySusan to prevent overshoot
     * mechanisms used by the tracker module  
     */
    
    public void stopLazySusan()
    {
        lazySusanSpeed = 0.0;
        //check_ls_position();
        lsVictor.set(0.0);
        current_lazySusanSpeed = lazySusanSpeed;
 
        System.out.println("Halting LazySusan!");
 
        
    }
    
    public void shootBall()
    {
        RoboRebels.isShooting = true; 
        return;  
    }
    
    
    /**
     * This method retracts the shooter by making use of the accelerometer in 
     * RRTracker
     */
    public void retractShooter()
    {
        isRetracting = true;
    }
    
    public void expandShooter()
    {
        isExpanding = true;
    }
    
    public int check_ls_position()
    {
        
        double calibration_factor = 46.0;  // converts lazySusanSpeed to angular velocity in degrees per tick
                                          // 23.0 is approximate value for 0.2 LS SPEED
        
        System.out.println("Checking Position...");
        
        double time_current = Timer.getFPGATimestamp();
        
        RoboRebels.angle_position += current_lazySusanSpeed * calibration_factor * (time_current - RoboRebels.time_last_update);
        
        RoboRebels.time_last_update = time_current;
        
        RoboRebels.printLCD(5, "LS Position: " + RRTracker.round(RoboRebels.angle_position)); 
        
        System.out.println("LS Position: " + RRTracker.round(RoboRebels.angle_position));
        
        int result = RoboRebels.OK;                 // Return indication if at either limit
        if (RoboRebels.angle_position > 45.0)       // Make 80 degrees when done testing
            result = RoboRebels.AT_RIGHT_LIMIT;
        else if (RoboRebels.angle_position < -45.0) // Make -80 degrees when done testing
            result = RoboRebels.AT_LEFT_LIMIT;
       
        return (result);
    }
    
    public void reset()
    {
        tracking = false;
        isRetracting = false;
        isExpanding = false;
        ball_present = false;
        
    }
    
}
