/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esa.snap.gui.actions.file;

import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.beam.dataio.dimap.DimapProductHelpers;
import org.esa.beam.dataio.dimap.DimapProductReader;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.jai.BandOpImage;
import org.esa.snap.gui.SnapApp;
import org.esa.snap.gui.SnapDialogs;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.MessageFormat;

/**
 * Action which saves a selected product using a new name.
 *
 * @author Norman
 */
@ActionID(
        category = "File",
        id = "org.esa.snap.gui.actions.file.SaveProductAsAction"
)
@ActionRegistration(
        displayName = "#CTL_SaveProductAsActionName"
)
@ActionReference(path = "Menu/File", position = 51, separatorAfter = 59)
@NbBundle.Messages({
        "CTL_SaveProductAsActionName=Save Product As..."
})
public final class SaveProductAsAction extends AbstractAction {

    public static final String PREFERENCES_KEY_PRODUCT_CONVERSION_REQUIRED = "product_conversion_required";
    private final WeakReference<Product> productRef;

    public SaveProductAsAction(Product products) {
        productRef = new WeakReference<>(products);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        execute();
    }

    /**
     * Executes the action command.
     *
     * @return {@code Boolean.TRUE} on success, {@code Boolean.FALSE} on failure, or {@code null} on cancellation.
     */
    public Boolean execute() {
        Product product = productRef.get();
        if (product != null) {
            return saveProductAs(product);
        } else {
            // reference was garbage collected, that's fine, no need to save.
            return true;
        }
    }

    private Boolean saveProductAs(Product product) {

        ProductReader reader = product.getProductReader();
        if (reader != null && !(reader instanceof DimapProductReader)) {
            SnapDialogs.Answer answer = SnapDialogs.requestDecision("Save Product As",
                                                                    MessageFormat.format("In order to save the product\n" +
                                                                                                 "   {0}\n" +
                                                                                                 "it has to be converted to the BEAM-DIMAP format.\n" +
                                                                                                 "Depending on the product size the conversion also may take a while.\n\n" +
                                                                                                 "Do you really want to convert the product now?\n",
                                                                                         product.getDisplayName()),
                                                                    true,
                                                                    PREFERENCES_KEY_PRODUCT_CONVERSION_REQUIRED);
            if (answer == SnapDialogs.Answer.NO) {
                return false;
            } else if (answer == SnapDialogs.Answer.CANCELLED) {
                return null;
            }
        }

        String fileName;
        if (product.getFileLocation() != null) {
            fileName = product.getFileLocation().getName();
        } else {
            fileName = product.getName();
        }
        File newFile = SnapDialogs.requestFileForSave("Save Product As",
                                                      false,
                                                      DimapProductHelpers.createDimapFileFilter(),
                                                      DimapProductConstants.DIMAP_HEADER_FILE_EXTENSION,
                                                      fileName,
                                                      OpenProductAction.PREFERENCES_KEY_LAST_PRODUCT_DIR);
        if (newFile == null) {
            // cancelled
            return null;
        }

        String oldProductName = product.getName();
        File oldFile = product.getFileLocation();

        //  For DIMAP products, check if file path has really changed
        //  if not, just save product
        if (reader instanceof DimapProductReader && newFile.equals(oldFile)) {
            return new SaveProductAction(product).execute();
        }

        product.setFileLocation(newFile);

        Boolean status = new SaveProductAction(product).execute();
        if (Boolean.TRUE.equals(status)) {
            try {
                attachNewDimapReaderInstance(product, newFile);
            } catch (IOException e) {
                SnapApp.getDefault().handleError("Failed to reopen product", e);
            }
        } else {
            product.setFileLocation(oldFile);
            product.setName(oldProductName);
        }

        return status;
    }


    private void attachNewDimapReaderInstance(Product product, File newFile) throws IOException {
        DimapProductReader productReader = (DimapProductReader) ProductIO.getProductReader(DimapProductConstants.DIMAP_FORMAT_NAME);
        productReader.bindProduct(newFile, product);
        product.setProductReader(productReader);
        Band[] bands = product.getBands();
        for (Band band : bands) {
            if (band.isSourceImageSet() && band.getSourceImage().getImage(0) instanceof BandOpImage) {
                band.setSourceImage(null);
            }
        }
    }

}