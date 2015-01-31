package org.stlpriory.robotics.commands;

import edu.wpi.first.wpilibj.command.Command;

import org.stlpriory.robotics.Robot;

public class ElevatorStop extend Command {
	boolean = commandExecuted
    public ElevatorStop() {
        // Use requires() here to declare subsystem dependencies
        requires(Robot.exampleSubsystem);
    }

    // Called just before this Command runs the first time
    protected void initialize() {
    	commandExecuted = false
    }

    // Called repeatedly when this Command is scheduled to run
    protected void execute() {
    	Elevator.stop();
    	commandExecuted = true
    }

    // Make this return true when this Command no longer needs to run execute()
    protected boolean isFinished() {
        return false;
    }

    // Called once after isFinished returns true
    protected void end() {
    }

    // Called when another command which requires one or more of the same
    // subsystems is scheduled to run
    protected void interrupted() {
    }

}
