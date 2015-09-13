/*
 * ShootOFF - Software for Laser Dry Fire Training
 * Copyright (C) 2015 phrack
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.shootoff.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.shootoff.config.Configuration;
import com.shootoff.targets.ImageRegion;
import com.shootoff.targets.RegionType;
import com.shootoff.targets.TargetRegion;
import com.shootoff.targets.animation.SpriteAnimation;

import javafx.animation.Animation.Status;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
 
/**
 * This class wraps a group that represents a target so that the target
 * can be moved and resized using the mouse and individual regions
 * can be animated.
 * 
 * @author phrack
 */
public class Target {
	private static final int MOVEMENT_DELTA = 1;
	private static final int SCALE_DELTA = 1;
    private static final int RESIZE_MARGIN = 5;
    
    private final File targetFile;
    private final Group targetGroup;
    private final Optional<Configuration> config;
    private final Optional<CanvasManager> parent;
    private final Optional<List<Target>> targets;
    private final boolean userDeletable;
    private final String cameraName;
    private final int targetIndex;
    private boolean move;
    private boolean resize;
    private boolean top;
    private boolean bottom;
    private boolean left;
    private boolean right;
    
    private double x;
    private double y;
    
    public Target(File targetFile, Group target, Configuration config, CanvasManager parent,
    		boolean userDeletable, int targetIndex) {
    	this.targetFile = targetFile;
        this.targetGroup = target;
        this.config = Optional.of(config);
        this.parent = Optional.of(parent);
        this.targets = Optional.empty();
        this.userDeletable = userDeletable;
        this.cameraName = parent.getCameraName();
        this.targetIndex = targetIndex;
        
        mousePressed();
        mouseDragged();
        mouseMoved();
        mouseReleased();
        keyPressed();
    }
    
    public Target(Group target, List<Target> targets) {
    	this.targetFile = null;
        this.targetGroup = target;
        this.config = Optional.empty();
        this.parent = Optional.empty();
        this.targets = Optional.of(targets);
        this.userDeletable = false;
        this.cameraName = null;
        this.targetIndex = 0;
        
        mousePressed();
        mouseDragged();
        mouseMoved();
        mouseReleased();
        keyPressed();
    }
    
    public File getTargetFile() {
    	return targetFile;
    }
    
    public Group getTargetGroup() {
    	return targetGroup;
    }
    
    public int getTargetIndex() {
    	return targetIndex;
    }
    
    public void setPosition(double x, double y) {
    	targetGroup.setLayoutX(x);
    	targetGroup.setLayoutY(y);
    }
    
    public Point2D getPosition() {
    	return new Point2D(targetGroup.getLayoutX(), targetGroup.getLayoutY());
    }
    
    public void setDimensions(double newWidth, double newHeight) { 	
		double currentWidth = targetGroup.getBoundsInParent().getWidth();
		double currentHeight = targetGroup.getBoundsInParent().getHeight();
    	
		if (currentWidth != newWidth) {
			double scaleXDelta = 1.0 + ((newWidth - currentWidth) / currentWidth);
			targetGroup.setScaleX(targetGroup.getScaleX() * scaleXDelta);
		}
		
		if (currentHeight != newHeight) {
			double scaleYDelta = 1.0 + ((newHeight - currentHeight) / currentHeight);
			targetGroup.setScaleY(targetGroup.getScaleY() * scaleYDelta);
		}
    }
    
    public Dimension2D getDimension() {
    	return new Dimension2D(targetGroup.getBoundsInParent().getWidth(), 
    			targetGroup.getBoundsInParent().getHeight());
    }
    
    protected static void parseCommandTag(TargetRegion region, CommandProcessor commandProcessor) {
		String commandsSource = region.getTag("command");
		List<String> commands = Arrays.asList(commandsSource.split(";"));		
		
		for (String command : commands) {
			int openParen = command.indexOf('(');
			String commandName;
			List<String> args;
			
			if (openParen > 0) {
				commandName = command.substring(0, openParen);
				args = Arrays.asList(command.substring(openParen + 1, command.indexOf(')')).split(","));
			} else {
				commandName = command;
				args = new ArrayList<String>();
			}
			
			commandProcessor.process(commands, commandName, args);
		}
    }
    
	protected static Optional<TargetRegion> getTargetRegionByName(List<Target> targets, TargetRegion region, String name) {
		for (Target target : targets) {
			if (target.getTargetGroup().getChildren().contains(region)) {
				for (Node node : target.getTargetGroup().getChildren()) {
					TargetRegion r = (TargetRegion)node;
					if (r.tagExists("name") && r.getTag("name").equals(name)) return Optional.of(r);
				}
			}
		}
		
		return Optional.empty();
	}
    
	public void animate(TargetRegion region, List<String> args) {
		ImageRegion imageRegion;
		
		if (args.size() == 0) {
			imageRegion = (ImageRegion)region;
		} else {
			Optional<TargetRegion> r;
			
			if (targets.isPresent()) {
				r = getTargetRegionByName(targets.get(), region, args.get(0));
			} else if (parent.isPresent()) {
				r = getTargetRegionByName(parent.get().getTargets(), region, args.get(0));
			} else {
				r = Optional.empty();
			}
			
			if (r.isPresent()) {
				imageRegion = (ImageRegion)r.get();
			} else {
				System.err.format("Request to animate region named %s, but it "
						+ "doesn't exist.", args.get(0));
				return;
			}
		}
		
		// Don't repeat animations for fallen targets
		if (!imageRegion.onFirstFrame()) return;
		
		if (imageRegion.getAnimation().isPresent()) {
			imageRegion.getAnimation().get().play();
		} else {
			System.err.println("Request to animate region, but region does "
					+ "not contain an animation.");
		}
	}
	
	public void reverseAnimation(TargetRegion region) {
		if (region.getType() != RegionType.IMAGE) {
			System.err.println("A reversal was requested on a non-image region.");
			return;
		}
		
		ImageRegion imageRegion = (ImageRegion)region;
		if (imageRegion.getAnimation().isPresent()) {
			SpriteAnimation animation = imageRegion.getAnimation().get();

			if (animation.getStatus() == Status.RUNNING) {
				animation.setOnFinished((e) -> {
						animation.reverse();
						animation.setOnFinished(null);
					});
			} else {
				animation.reverse();
			}
		} else {
			System.err.println("A reversal was requested on an image region that isn't animated.");
		}
	}
    
    private void mousePressed() {
        targetGroup.setOnMousePressed((event) -> {
	        if(!isInResizeZone(event)) {
	        	move = true;
	        	
	            return;
	        }
	        
	        resize = true;
	        top = isTopZone(event);
	        bottom = isBottomZone(event);
	        left = isLeftZone(event);
	        right = isRightZone(event);
        });
    }
 
    private void mouseDragged() {
    	targetGroup.setOnMouseDragged((event) -> {
    		if (!resize && !move) return;
    		
	        if(move) {
	        	if (config.isPresent() && config.get().inDebugMode() && (event.isControlDown() || event.isShiftDown())) return;

        		double deltaX = event.getX() - x;
        		double deltaY = event.getY() - y;
        	        	
	            targetGroup.setLayoutX(targetGroup.getLayoutX() + deltaX);
	            targetGroup.setLayoutY(targetGroup.getLayoutY() + deltaY);
	        	
				if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
					config.get().getSessionRecorder().get().recordTargetMoved(cameraName, 
							this, (int)targetGroup.getLayoutX(), (int)targetGroup.getLayoutY());
				}
	        	
	            return;
	        }
	 
	        if (left || right) {
		        double gap; // The gap between the mouse and nearest target edge
		        
		        if (right) {
		        	gap = event.getX() - targetGroup.getLayoutBounds().getMaxX();
		        } else {
		        	gap = event.getX() - targetGroup.getLayoutBounds().getMinX();
		        }
		        
		        double currentWidth = targetGroup.getBoundsInParent().getWidth(); 
		        double newWidth = currentWidth + gap;
		        double scaleDelta = (newWidth - currentWidth) / currentWidth;
		       
		        double currentOriginX = targetGroup.getBoundsInParent().getMinX();
		        double newOriginX;
		        
		        if (right) {
		        	scaleDelta *= -1.0;
		        	newOriginX = currentOriginX - ((newWidth - currentWidth) / 2);
		        } else {
		        	newOriginX = currentOriginX + ((newWidth - currentWidth) / 2);
		        }
		        
		        double originXDelta = newOriginX - currentOriginX;
		        
		        if (right) originXDelta *= -1.0;
		        
	        	targetGroup.setLayoutX(targetGroup.getLayoutX() + originXDelta);
	        	targetGroup.setScaleX(targetGroup.getScaleX() * (1.0 - scaleDelta));
    		} else if (top || bottom) {
		        double gap;
		        
		        if (bottom) {
		        	gap = event.getY() - targetGroup.getLayoutBounds().getMaxY();
		        } else {
		        	gap = event.getY() - targetGroup.getLayoutBounds().getMinY();
		        }    
		        
		        double currentHeight = targetGroup.getBoundsInParent().getHeight(); 
		        double newHeight = currentHeight + gap;
		        double scaleDelta = (newHeight - currentHeight) / currentHeight;
		       
		        double currentOriginY = targetGroup.getBoundsInParent().getMinY();
		        double newOriginY;
		        
		        if (bottom) {
		        	scaleDelta *= -1.0;
		        	newOriginY = currentOriginY - ((newHeight - currentHeight) / 2);
		        } else {
		        	newOriginY = currentOriginY + ((newHeight - currentHeight) / 2);
		        }
		        
		        double originYDelta = newOriginY - currentOriginY;
		        
		        if (bottom) originYDelta *= -1.0;
		   
	        	targetGroup.setLayoutY(targetGroup.getLayoutY() + originYDelta); 
	        	targetGroup.setScaleY(targetGroup.getScaleY() * (1.0 - scaleDelta));
	        }
	        
			if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
				config.get().getSessionRecorder().get().recordTargetMoved(cameraName, 
						this, (int)targetGroup.getLayoutX(), (int)targetGroup.getLayoutY());
			}
			
			if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
				config.get().getSessionRecorder().get().recordTargetResized(cameraName, 
						this, targetGroup.getBoundsInParent().getWidth(), 
						targetGroup.getBoundsInParent().getHeight());
			}
    	});
    }
    
    private void mouseMoved() {
    	targetGroup.setOnMouseMoved((event) -> {
        	x = event.getX();
        	y = event.getY();
    		
    		if (isTopZone(event)) {
    			targetGroup.setCursor(Cursor.N_RESIZE);
    		} else if (isBottomZone(event)) {
	       		targetGroup.setCursor(Cursor.S_RESIZE);
	       	} else if (isLeftZone(event)) {
	       		targetGroup.setCursor(Cursor.W_RESIZE);
	       	} else if (isRightZone(event)) {
	       		targetGroup.setCursor(Cursor.E_RESIZE);
	       	} else {
	       		targetGroup.setCursor(Cursor.DEFAULT);
	        }
    	});
    }
    
    private void mouseReleased() {
    	targetGroup.setOnMouseReleased((event) -> {    		
    		resize = false;
    		move = false;
        	targetGroup.setCursor(Cursor.DEFAULT);
    	});
    }
    
    private void keyPressed() {
		targetGroup.setOnKeyPressed((event) -> {	
			double currentWidth = targetGroup.getBoundsInParent().getWidth();
			double currentHeight = targetGroup.getBoundsInParent().getHeight();
			
			switch (event.getCode()) {
			case DELETE:
				if (userDeletable && parent.isPresent()) parent.get().removeTarget(this);
				break;
				
			case LEFT:
				{
					double newWidth = currentWidth + SCALE_DELTA;
					double scaleDelta = (newWidth - currentWidth) / currentWidth;
					
					if (event.isShiftDown()) {
						targetGroup.setScaleX(targetGroup.getScaleX() * (1.0 - scaleDelta));
						
						if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
							config.get().getSessionRecorder().get().recordTargetResized(cameraName, 
									this, targetGroup.getBoundsInParent().getWidth(), 
									targetGroup.getBoundsInParent().getHeight());
						}
					} else {
						targetGroup.setLayoutX(targetGroup.getLayoutX() - MOVEMENT_DELTA);
						
						if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
							config.get().getSessionRecorder().get().recordTargetMoved(cameraName, 
									this, (int)targetGroup.getLayoutX(), (int)targetGroup.getLayoutY());
						}
					}
				}

				break;
				
			case RIGHT:
				{
					double newWidth = currentWidth - SCALE_DELTA;
					double scaleDelta = (newWidth - currentWidth) / currentWidth;

					if (event.isShiftDown()) {
						targetGroup.setScaleX(targetGroup.getScaleX() * (1.0 - scaleDelta));
						
						if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
							config.get().getSessionRecorder().get().recordTargetResized(cameraName, 
									this, targetGroup.getBoundsInParent().getWidth(), 
									targetGroup.getBoundsInParent().getHeight());
						}
					} else {
						targetGroup.setLayoutX(targetGroup.getLayoutX() + MOVEMENT_DELTA);
						
						if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
							config.get().getSessionRecorder().get().recordTargetMoved(cameraName, 
									this, (int)targetGroup.getLayoutX(), (int)targetGroup.getLayoutY());
						}
					}
				}
				
				break;
				
			case UP:
				{
					double newHeight = currentHeight + SCALE_DELTA;
					double scaleDelta = (newHeight - currentHeight) / currentHeight;
					
					if (event.isShiftDown()) {
						targetGroup.setScaleY(targetGroup.getScaleY() * (1.0 - scaleDelta));
						
						if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
							config.get().getSessionRecorder().get().recordTargetResized(cameraName, 
									this, targetGroup.getBoundsInParent().getWidth(), 
									targetGroup.getBoundsInParent().getHeight());
						}
					} else {
						targetGroup.setLayoutY(targetGroup.getLayoutY() - MOVEMENT_DELTA);
						
						if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
							config.get().getSessionRecorder().get().recordTargetMoved(cameraName, 
									this, (int)targetGroup.getLayoutX(), (int)targetGroup.getLayoutY());
						}
					}
				}
				
				break;

			case DOWN:
				{
					double newHeight = currentHeight - SCALE_DELTA;
					double scaleDelta = (newHeight - currentHeight) / currentHeight;
					
					if (event.isShiftDown()) {
						targetGroup.setScaleY(targetGroup.getScaleY() * (1.0 - scaleDelta));
						
						if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
							config.get().getSessionRecorder().get().recordTargetResized(cameraName, 
									this, targetGroup.getBoundsInParent().getWidth(), 
									targetGroup.getBoundsInParent().getHeight());
						}
					} else {
						targetGroup.setLayoutY(targetGroup.getLayoutY() + MOVEMENT_DELTA);
						
						if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
							config.get().getSessionRecorder().get().recordTargetMoved(cameraName, 
									this, (int)targetGroup.getLayoutX(), (int)targetGroup.getLayoutY());
						}
					}
				}
				
				break;
				
			default:
				break;
			}
			event.consume();
		});
    }
  
    private boolean isTopZone(MouseEvent event) {
    	return event.getY() < (targetGroup.getLayoutBounds().getMinY() + RESIZE_MARGIN);	
    }
    
    private boolean isBottomZone(MouseEvent event) {
    	return event.getY() > (targetGroup.getLayoutBounds().getMaxY() - RESIZE_MARGIN);
    }
    
    private boolean isLeftZone(MouseEvent event) {
    	return event.getX() < (targetGroup.getLayoutBounds().getMinX() + RESIZE_MARGIN);	
    }
    
    private boolean isRightZone(MouseEvent event) {
    	return event.getX() > (targetGroup.getLayoutBounds().getMaxX() - RESIZE_MARGIN) ;
    }
 
    private boolean isInResizeZone(MouseEvent event) { 
        return isTopZone(event) || isBottomZone(event) || isLeftZone(event) ||
        		isRightZone(event);
    }   
}