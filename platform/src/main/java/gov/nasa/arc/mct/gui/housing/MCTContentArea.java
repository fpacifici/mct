/*******************************************************************************
 * Mission Control Technologies, Copyright (c) 2009-2012, United States Government
 * as represented by the Administrator of the National Aeronautics and Space 
 * Administration. All rights reserved.
 *
 * The MCT platform is licensed under the Apache License, Version 2.0 (the 
 * "License"); you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations under 
 * the License.
 *
 * MCT includes source code licensed under additional open source licenses. See 
 * the MCT Open Source Licenses file included with this distribution or the About 
 * MCT Licenses dialog available at runtime from the MCT Help menu for additional 
 * information. 
 *******************************************************************************/
/*
 * MCTContentArea.java - Aug. 18, 2008.
 *
 * This code is property of the National Aeronautics and Space Administration
 * and was produced for the Mission Control Technologies (MCT) Project.
 * 
 */
package gov.nasa.arc.mct.gui.housing;

import gov.nasa.arc.mct.components.AbstractComponent;
import gov.nasa.arc.mct.gui.CompositeViewManifestationProvider;
import gov.nasa.arc.mct.gui.SelectionProvider;
import gov.nasa.arc.mct.gui.Twistie;
import gov.nasa.arc.mct.gui.View;
import gov.nasa.arc.mct.gui.ViewProvider;
import gov.nasa.arc.mct.gui.housing.MCTHousing.ControlProvider;
import gov.nasa.arc.mct.gui.impl.ActionContextImpl;
import gov.nasa.arc.mct.gui.menu.MenuFactory;
import gov.nasa.arc.mct.platform.spi.PlatformAccess;
import gov.nasa.arc.mct.services.component.ViewInfo;
import gov.nasa.arc.mct.services.component.ViewType;
import gov.nasa.arc.mct.util.LafColor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Framework for holding the MCT Content Area (often refereed to as "the canvas") including its title bar and
 * control panel. 
 */

@SuppressWarnings("serial")
public class MCTContentArea extends JPanel implements CompositeViewManifestationProvider, SelectionProvider, ControlProvider {

    public static final String CENTER_PANE_VIEW_CHANGE = "center-pane-view-change";
    private MCTHousing parentHousing;
    private final AbstractComponent ownerComponent;
    private CanvasTitleArea titleBar;
    private View ownerComponentCanvasManifestation = null; // The current contained view manifestation.

    private JSplitPane splitPane = null; // hold owner component canvas manifestation and its control area. 

    private boolean canvasTitleBarShowing = true;
    private JComponent controlManifestation = null;
    private int dividerSize = 0; // size of split pane's divider. 
    
    private final Map<ViewInfo, ViewProvider> housedManifestations = new HashMap<ViewInfo, ViewProvider>();

    private final JLabel STALE_LABEL = new JLabel("*STALE*");
    private final PropertyChangeListener selectionListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
        }
    }; 

    private final PropertyChangeListener objectStaleStateListener = new PropertyChangeListener() {
        
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            markStale((Boolean)evt.getNewValue());
        }
    };
    public MCTContentArea(MCTHousing parentHousing, AbstractComponent ownerComponent, View initialViewManifestation) {
        super(new BorderLayout());
        this.parentHousing = parentHousing;
        this.ownerComponent = ownerComponent;
        if (ownerComponentCanvasManifestation == null) {
            AbstractComponent clonedComponent = PlatformAccess.getPlatform().getPersistenceProvider().getComponent(ownerComponent.getComponentId());
            ownerComponentCanvasManifestation = ((initialViewManifestation != null) 
                    ? initialViewManifestation
                            : clonedComponent.getViewInfos(ViewType.CENTER).iterator().next().createView(clonedComponent));
        }

        assert ownerComponentCanvasManifestation != null;  
        setOwnerComponentCanvasManifestation(ownerComponentCanvasManifestation);
        this.revalidate();
        this.parentHousing.setContentArea(this);
        STALE_LABEL.setForeground(Color.red);
    }

    public MCTContentArea(MCTHousing parentHousing, AbstractComponent ownerComponent) {
        this(parentHousing, ownerComponent, null);
    }

    public MCTContentArea(MCTHousing parentHousing, View initialViewManifestation) {
        this(parentHousing, initialViewManifestation.getManifestedComponent(), initialViewManifestation);
    }
    
    @Override
    public Collection<View> getSelectedManifestations() {
        return ownerComponentCanvasManifestation.getSelectionProvider().getSelectedManifestations();
    }
    
    public void showControl(boolean flag) {   
       if (splitPane != null) {
            if (!flag) {
                splitPane.setDividerLocation(0);
            } else if (controlManifestation.getPreferredSize().getHeight() < 200)  {
                // Small control manifesation. Give it a small percentage of the panes.
                 splitPane.setDividerLocation((int) controlManifestation.getPreferredSize().getHeight());
             } else {
                 splitPane.setDividerLocation(-1);
                 splitPane.setResizeWeight(0.66); 
             }
            splitPane.setDividerSize(flag ? dividerSize : 0);
            if (controlManifestation != null) {
               controlManifestation.setVisible(flag);
            }
            revalidate();
       }
    }

    public void markStale(boolean isStale) {
        MCTStatusArea statusArea = parentHousing.getStatusArea();
        if (statusArea == null)
            return;
        if (isStale)
            statusArea.addToLeft(STALE_LABEL);
        else
            statusArea.removeFromLeft(STALE_LABEL);
    }
    
    private void setupSelectionPropertyChangeListener(View oldManifestation) {
        if (oldManifestation != null) {
            oldManifestation.getSelectionProvider().removeSelectionChangeListener(selectionListener);
        }

        if (ownerComponentCanvasManifestation != null) {
            ownerComponentCanvasManifestation.getSelectionProvider().addSelectionChangeListener(selectionListener);
        }
    }

    public MCTContentArea() {
        super(new BorderLayout());
        ownerComponent = null;
    }

    public boolean isTitleBarShowing() {
        return this.canvasTitleBarShowing;
    }

    public void showCanvasTitle(boolean doShow) {
        canvasTitleBarShowing = doShow;
        if (doShow) {
            add(this.titleBar, BorderLayout.NORTH);
        } else {
            remove(this.titleBar);
            showControl(false);
            titleBar.setTitleAreaVisible(false);
        }
        doLayout();
        validate();
    }

    public JComponent getContentAreaPane() {
        return this.ownerComponentCanvasManifestation;
    }

    public void setParentHousing(MCTHousing parentHousing) {
        this.parentHousing = parentHousing;
    }
    
    /**
     * Set the owner view manifestation and update the GUI to display it. 
     * @param viewManifestation
     */
    public void setOwnerComponentCanvasManifestation(View viewManifestation) {
 
        if (viewManifestation == null) {
            throw new IllegalArgumentException("viewManifestation argument cannot be null");
        }

        ownerComponentCanvasManifestation.removePropertyChangeListener(View.VIEW_STALE_PROPERTY, objectStaleStateListener);
        markStale(false);
        viewManifestation.addPropertyChangeListener(View.VIEW_STALE_PROPERTY, objectStaleStateListener); 
        
        housedManifestations.put(viewManifestation.getInfo(), viewManifestation);

        if (ownerComponentCanvasManifestation != null) {
            // clean out previous manifestation's swing components if there is one.
            removeAll();
            SelectionProvider provider = ownerComponentCanvasManifestation.getSelectionProvider();
            Collection<View> selections = provider.getSelectedManifestations();
            if (!selections.isEmpty()) {
                provider.clearCurrentSelections();
                firePropertyChange(SelectionProvider.SELECTION_CHANGED_PROP, selections, Collections.emptyList());
            }
        }
        
        // setup new manifestation.
        View oldManifestation = ownerComponentCanvasManifestation;
        ownerComponentCanvasManifestation = viewManifestation;
        setupSelectionPropertyChangeListener(oldManifestation);
        
        controlManifestation = ownerComponentCanvasManifestation.getControlManifestation();
        
        // Setup title bar
        titleBar = new CanvasTitleArea(ownerComponentCanvasManifestation.getInfo().getViewName());
    
        // Setup scroll pane.
        JScrollPane scrollPane = new JScrollPane(ownerComponentCanvasManifestation, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Stop scroll bars responding to arrow keys
        scrollPane.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("LEFT"), "doNothing");
        scrollPane.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("RIGHT"), "doNothing");
        scrollPane.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("LEFT"), "doNothing");
        scrollPane.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("RIGHT"), "doNothing");

        // Attach control manifestation.
        if (controlManifestation != null) {
            controlManifestation.setAlignmentX(Component.LEFT_ALIGNMENT);
            // use a split pane to separate control manifestation from canvas manifestation.
            splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, controlManifestation, scrollPane);          
            splitPane.setOneTouchExpandable(true);
            splitPane.setContinuousLayout(true);
            splitPane.setBorder(null);
            dividerSize = splitPane.getDividerSize();
            showControl(false);   // default is that formatting control is not visible. 
            showCanvasTitle(canvasTitleBarShowing);
            add(splitPane);
            instrumentNames(splitPane);
        } else {
            // Just add the canvas as we there is no control manifestation. 
            splitPane = null;
            showCanvasTitle(canvasTitleBarShowing);
            add(scrollPane);
        }
       
        
        revalidate();  
    }

    private void instrumentNames(JSplitPane pane) {
        pane.setName("contentAreaSplitPane");
    }

    @Override
    public void addSelectionChangeListener(PropertyChangeListener listener) {
        addPropertyChangeListener(SelectionProvider.SELECTION_CHANGED_PROP, listener);
    }

    @Override
    public void removeSelectionChangeListener(PropertyChangeListener listener) {
        removePropertyChangeListener(SelectionProvider.SELECTION_CHANGED_PROP, listener);
    }
    
    @Override
    public void clearCurrentSelections() {
        ownerComponentCanvasManifestation.getSelectionProvider().clearCurrentSelections();
    }

    public AbstractComponent getOwnerComponent() {
        return this.ownerComponent;
    }

    @Override
    public View getHousedViewManifestation() {
        return this.ownerComponentCanvasManifestation;
    }
    
    @Override
    public Collection<ViewProvider> getHousedManifestationProviders() {
        return this.housedManifestations.values();
    }
    
    /**
     * Determines whether the content area is empty or not. The initial algorithm will look
     * at the current manifestation and determine whether the swing hierarchy contains any other
     * MCTViewManifestations. This is not optimal as this relies on the current structure of 
     * a canvas view. In the future this method may change to invoke a more specific method on the 
     * view manifestation. 
     * @return true if the the content area is empty, false otherwise.
     */
    public boolean isAreaEmpty() {
        View manifestation = getHousedViewManifestation();
        return manifestation == null ||
               !containsMCTViewManifestation(manifestation);
    }

    private boolean containsMCTViewManifestation(Container parent) {
        for (Component c:parent.getComponents()) {
            if (c instanceof View) {
                return true;
            }
            if (c instanceof Container &&
                containsMCTViewManifestation(Container.class.cast(c))) {
                return true;
            }
        }
        return false;
    }
    
    void clearHousedManifestations() {
        housedManifestations.clear();
    }
    
    private class CanvasTitleArea extends JPanel {
        private final Color BACKGROUND_COLOR = LafColor.WINDOW_BORDER.darker();
        private final Color FOREGROUND_COLOR = LafColor.WINDOW.brighter();
        private static final int HORIZONTAL_SPACING = 5;
        private final JLabel title;

        private Twistie controlTwistie;
        
        private class ControlAreaTwistie extends Twistie {
                @Override
                protected void changeStateAction(boolean state) {
                    showControl(state);
                }    
        }
    
        /**
         * Inform the title area that it has been hidden and that it
         * should update its visual state appropriately. 
         */
        public void setTitleAreaVisible(boolean state){
            if (controlTwistie!=null) {
              controlTwistie.changeState(state);
            }
        }
        
        public CanvasTitleArea (String text) {
            setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
            setBackground(BACKGROUND_COLOR);

            title = new JLabel(text);
            title.setForeground(FOREGROUND_COLOR);
            add(Box.createHorizontalStrut(HORIZONTAL_SPACING));
            add(title);
            add(Box.createHorizontalStrut(HORIZONTAL_SPACING));

            // Only add Twistie if control manifestation not null. 
            if (controlManifestation != null) {
                controlTwistie = new ControlAreaTwistie();
                add(controlTwistie);         
            }
            // Add popup listener to title bar.      
            MouseListener popupListener = new PopupListener();
            this.addMouseListener(popupListener);

            instrumentNamesInCanvasTitle(title);
        }

        private void instrumentNamesInCanvasTitle(JLabel title) {
            this.setName("canvasTitleArea");
            title.setName("title");
        }
    }

    class PopupListener extends MouseAdapter {
        // Variables used by unit test. 
        boolean testMode = false;
        boolean popupActivated = false;

        @Override
        public void mousePressed(MouseEvent e) {
            processPopUpListenerEvent(e, true);
        }

        /**
         * Respond to mouse event in the canvas area by either showing a popup menu if appropriate
         * or noting that items are no longer selected.
         * @param e the event.
         * @param mousePressed state of the mouse button.
         */
        private void processPopUpListenerEvent(MouseEvent e, boolean mousePressed) {
            if (e.isPopupTrigger()) {
                ActionContextImpl context = new ActionContextImpl();
                context.setTargetComponent(ownerComponentCanvasManifestation.getManifestedComponent());
                context.setTargetHousing((MCTHousing) SwingUtilities.getAncestorOfClass(MCTAbstractHousing.class,
                        ownerComponentCanvasManifestation));
                JPopupMenu popup = MenuFactory.createViewPopupMenu(context);
                assert popup != null;
                if (!testMode) {
                    popup.show(e.getComponent(), e.getX(), e.getY());
                } else {
                    popupActivated = true;
                }
            }
        }
        
        // Test methods only to be utilized by unit tests. 
        void setTestMode() {
            testMode = true;
            popupActivated = false;
        }

        boolean popupActivated() {
            return popupActivated;
        }  
    }


}
