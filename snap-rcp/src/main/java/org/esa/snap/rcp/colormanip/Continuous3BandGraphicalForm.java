/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.snap.rcp.colormanip;

import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.ValueRange;
import com.bc.ceres.binding.ValueSet;
import com.bc.ceres.swing.binding.BindingContext;
import org.esa.snap.framework.datamodel.Band;
import org.esa.snap.framework.datamodel.ColorPaletteDef;
import org.esa.snap.framework.datamodel.ImageInfo;
import org.esa.snap.framework.datamodel.ProductNodeEvent;
import org.esa.snap.framework.datamodel.RasterDataNode;
import org.esa.snap.framework.datamodel.Stx;
import org.esa.snap.framework.ui.ImageInfoEditorModel;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.beans.PropertyChangeEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class Continuous3BandGraphicalForm implements ColorManipulationChildForm {

    private final ColorManipulationForm parentForm;
    private final ImageInfoEditor2 imageInfoEditor;
    private final ImageInfoEditorSupport imageInfoEditorSupport;
    private final JPanel contentPanel;

    private final ImageInfoEditorModel3B[] models;
    private final RasterDataNode[] initialChannelSources;
    private final RasterDataNode[] currentChannelSources;
    private final List<RasterDataNode> channelSourcesList;
    private final MoreOptionsForm moreOptionsForm;

    private int channel;

    private static final String GAMMA_PROPERTY = "gamma";
    double gamma = 1.0;

    private static final String CHANNEL_SOURCE_NAME_PROPERTY = "channelSourceName";
    String channelSourceName = "";

    public Continuous3BandGraphicalForm(final ColorManipulationForm parentForm) {
        this.parentForm = parentForm;

        imageInfoEditor = new ImageInfoEditor2(parentForm);
        imageInfoEditorSupport = new ImageInfoEditorSupport(imageInfoEditor);

        moreOptionsForm = new MoreOptionsForm(this, parentForm.getFormModel().canUseHistogramMatching());
        models = new ImageInfoEditorModel3B[3];
        initialChannelSources = new RasterDataNode[3];
        currentChannelSources = new RasterDataNode[3];
        channelSourcesList = new ArrayList<>(32);
        channel = 0;

        final Property channelSourceNameModel = Property.createForField(this, CHANNEL_SOURCE_NAME_PROPERTY, "");
        JComboBox channelSourceNameBox = new JComboBox();
        channelSourceNameBox.setEditable(false);

        final Property gammaModel = Property.createForField(this, GAMMA_PROPERTY, 1.0);
        gammaModel.getDescriptor().setValueRange(new ValueRange(1.0 / 10.0, 10.0));
        gammaModel.getDescriptor().setDefaultValue(1.0);
        JTextField gammaField = new JTextField();
        gammaField.setColumns(6);
        gammaField.setHorizontalAlignment(JTextField.RIGHT);

        moreOptionsForm.getBindingContext().getPropertySet().addProperty(channelSourceNameModel);
        moreOptionsForm.getBindingContext().bind(CHANNEL_SOURCE_NAME_PROPERTY, channelSourceNameBox);

        moreOptionsForm.getBindingContext().getPropertySet().addProperty(gammaModel);
        moreOptionsForm.getBindingContext().bind(GAMMA_PROPERTY, gammaField);

        moreOptionsForm.addRow(new JLabel("Source band: "), channelSourceNameBox);
        moreOptionsForm.addRow(new JLabel("Gamma non-linearity: "), gammaField);

        final PropertyContainer propertyContainer = new PropertyContainer();
        propertyContainer.addProperty(Property.createForField(this, "channel", 0));
        propertyContainer.getProperty("channel").getDescriptor().setValueSet(new ValueSet(new Integer[]{0, 1, 2}));

        final BindingContext bindingContext = new BindingContext(propertyContainer);

        JRadioButton rChannelButton = new JRadioButton("Red");
        JRadioButton gChannelButton = new JRadioButton("Green");
        JRadioButton bChannelButton = new JRadioButton("Blue");
        rChannelButton.setName("rChannelButton");
        gChannelButton.setName("gChannelButton");
        bChannelButton.setName("bChannelButton");

        final ButtonGroup channelButtonGroup = new ButtonGroup();
        channelButtonGroup.add(rChannelButton);
        channelButtonGroup.add(gChannelButton);
        channelButtonGroup.add(bChannelButton);

        bindingContext.bind("channel", channelButtonGroup);
        bindingContext.addPropertyChangeListener("channel", evt -> acknowledgeChannel());

        final JPanel channelButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        channelButtonPanel.add(rChannelButton);
        channelButtonPanel.add(gChannelButton);
        channelButtonPanel.add(bChannelButton);

        contentPanel = new JPanel(new BorderLayout(2, 2));
        contentPanel.add(channelButtonPanel, BorderLayout.NORTH);
        contentPanel.add(imageInfoEditor, BorderLayout.CENTER);

        moreOptionsForm.getBindingContext().addPropertyChangeListener(GAMMA_PROPERTY,
                                                                      evt -> handleGammaChanged());
        moreOptionsForm.getBindingContext().addPropertyChangeListener(CHANNEL_SOURCE_NAME_PROPERTY,
                                                                      this::handleChannelSourceNameChanged);
    }

    public Component getContentPanel() {
        return contentPanel;
    }

    @Override
    public ColorManipulationForm getParentForm() {
        return parentForm;
    }

    @Override
    public void handleFormShown(FormModel formModel) {
        RasterDataNode[] rasters = formModel.getRasters();
        initialChannelSources[0] = rasters[0];
        initialChannelSources[1] = rasters[1];
        initialChannelSources[2] = rasters[2];
        updateFormModel(formModel);
        parentForm.revalidateToolViewPaneControl();
    }

    @Override
    public void handleFormHidden(FormModel formModel) {
        imageInfoEditor.setModel(null);
        channelSourcesList.clear();
        Arrays.fill(models, null);
        Arrays.fill(initialChannelSources, null);
        Arrays.fill(currentChannelSources, null);
    }

    @Override
    public void updateFormModel(FormModel formModel) {
        RasterDataNode[] rasters = formModel.getRasters();
        currentChannelSources[0] = rasters[0];
        currentChannelSources[1] = rasters[1];
        currentChannelSources[2] = rasters[2];

        final Band[] availableBands = formModel.getProduct().getBands();
        channelSourcesList.clear();
        appendToChannelSources(currentChannelSources);
        appendToChannelSources(initialChannelSources);
        appendToChannelSources(availableBands);

        for (int i = 0; i < models.length; i++) {
            ImageInfoEditorModel3B oldModel = models[i];
            models[i] = new ImageInfoEditorModel3B(parentForm.getFormModel().getModifiedImageInfo(), i);
            Continuous1BandGraphicalForm.setDisplayProperties(models[i], currentChannelSources[i]);
            if (oldModel != null) {
                models[i].setHistogramViewGain(oldModel.getHistogramViewGain());
                models[i].setMinHistogramViewSample(oldModel.getMinHistogramViewSample());
                models[i].setMaxHistogramViewSample(oldModel.getMaxHistogramViewSample());
            }
        }

        final String[] sourceNames = new String[channelSourcesList.size()];
        for (int i = 0; i < channelSourcesList.size(); i++) {
            sourceNames[i] = channelSourcesList.get(i).getName();
        }
        moreOptionsForm.getBindingContext().getPropertySet().getProperty(CHANNEL_SOURCE_NAME_PROPERTY).getDescriptor().setValueSet(new ValueSet(sourceNames));

        acknowledgeChannel();
    }

    private void appendToChannelSources(RasterDataNode[] rasterDataNodes) {
        for (RasterDataNode channelSource : rasterDataNodes) {
            if (!channelSourcesList.contains(channelSource)) {
                channelSourcesList.add(channelSource);
            }
        }
    }

    @Override
    public void resetFormModel(FormModel formModel) {
        updateFormModel(formModel);
        imageInfoEditor.computeZoomOutToFullHistogramm();
    }

    @Override
    public void handleRasterPropertyChange(ProductNodeEvent event, RasterDataNode raster) {
        ImageInfoEditorModel model = imageInfoEditor.getModel();
        if (model != null) {
            Continuous1BandGraphicalForm.setDisplayProperties(model, raster);
        }
        if (event.getPropertyName().equals(RasterDataNode.PROPERTY_NAME_STX)) {
            acknowledgeChannel();
        }
    }

    @Override
    public RasterDataNode[] getRasters() {
        return currentChannelSources.clone();
    }

    @Override
    public MoreOptionsForm getMoreOptionsForm() {
        return moreOptionsForm;
    }

    @Override
    public AbstractButton[] getToolButtons() {
        return new AbstractButton[]{
                imageInfoEditorSupport.autoStretch95Button,
                imageInfoEditorSupport.autoStretch100Button,
                imageInfoEditorSupport.zoomInVButton,
                imageInfoEditorSupport.zoomOutVButton,
                imageInfoEditorSupport.zoomInHButton,
                imageInfoEditorSupport.zoomOutHButton,
                imageInfoEditorSupport.showExtraInfoButton,
        };
    }

    private void acknowledgeChannel() {
        RasterDataNode channelSource = currentChannelSources[channel];
        final ImageInfoEditorModel3B model = models[channel];
        Continuous1BandGraphicalForm.setDisplayProperties(model, channelSource);
        imageInfoEditor.setModel(model);
        moreOptionsForm.getBindingContext().getBinding(CHANNEL_SOURCE_NAME_PROPERTY).setPropertyValue(channelSource.getName());
        moreOptionsForm.getBindingContext().getBinding(GAMMA_PROPERTY).setPropertyValue(model.getGamma());
    }

    private void handleGammaChanged() {
        imageInfoEditor.getModel().setGamma(gamma);
        parentForm.applyChanges();
    }

    private void handleChannelSourceNameChanged(PropertyChangeEvent evt) {
        RasterDataNode newChannelSource = null;
        for (RasterDataNode rasterDataNode : channelSourcesList) {
            if (rasterDataNode.getName().equals(channelSourceName)) {
                newChannelSource = rasterDataNode;
                break;
            }
        }
        if (newChannelSource == null) {
            JOptionPane.showMessageDialog(contentPanel,
                                          MessageFormat.format("Unknown band: ''{0}''", channelSourceName),
                                          "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final RasterDataNode oldChannelSource = currentChannelSources[channel];
        if (newChannelSource != oldChannelSource) {
            final Stx stx = parentForm.getStx(newChannelSource);
            if (stx != null) {
                currentChannelSources[channel] = newChannelSource;
                final ImageInfo imageInfo = parentForm.getFormModel().getModifiedImageInfo();
                imageInfo.getRgbChannelDef().setSourceName(channel, channelSourceName);
                final ImageInfo info = newChannelSource.getImageInfo(com.bc.ceres.core.ProgressMonitor.NULL);
                final ColorPaletteDef def = info.getColorPaletteDef();
                if (def != null) {
                    imageInfo.getRgbChannelDef().setMinDisplaySample(channel, def.getMinDisplaySample());
                    imageInfo.getRgbChannelDef().setMaxDisplaySample(channel, def.getMaxDisplaySample());
                }
                models[channel] = new ImageInfoEditorModel3B(imageInfo, channel);
                Continuous1BandGraphicalForm.setDisplayProperties(models[channel], newChannelSource);
                acknowledgeChannel();
                parentForm.applyChanges();
            } else {
                final Object value = evt.getOldValue();
                moreOptionsForm.getBindingContext().getBinding(CHANNEL_SOURCE_NAME_PROPERTY).setPropertyValue(value == null ? "" : value);
            }
        }
    }
}
