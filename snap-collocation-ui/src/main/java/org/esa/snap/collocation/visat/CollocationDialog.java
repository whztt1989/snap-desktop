/*
 * Copyright (C) 2014 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.snap.collocation.visat;

import org.esa.snap.collocation.CollocateOp;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.gpf.GPF;
import org.esa.snap.framework.gpf.OperatorSpi;
import org.esa.snap.framework.gpf.ui.OperatorMenu;
import org.esa.snap.framework.gpf.ui.OperatorParameterSupport;
import org.esa.snap.framework.gpf.ui.SingleTargetProductDialog;
import org.esa.snap.framework.ui.AppContext;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Ralf Quast
 */
class CollocationDialog extends SingleTargetProductDialog {

    public static final String HELP_ID = "collocation";

    private final OperatorParameterSupport parameterSupport;
    private final CollocationForm form;

    public CollocationDialog(AppContext appContext) {
        super(appContext, "Collocation", ID_APPLY_CLOSE, HELP_ID);
        final OperatorSpi operatorSpi = GPF.getDefaultInstance().getOperatorSpiRegistry().getOperatorSpi(CollocateOp.Spi.class.getName());

        parameterSupport = new OperatorParameterSupport(operatorSpi.getOperatorDescriptor());
        OperatorMenu operatorMenu = new OperatorMenu(this.getJDialog(),
                                                     operatorSpi.getOperatorDescriptor(),
                                                     parameterSupport,
                                                     appContext,
                                                     HELP_ID);

        getJDialog().setJMenuBar(operatorMenu.createDefaultMenu());

        form = new CollocationForm(parameterSupport.getPropertySet(), getTargetProductSelector(), appContext);

    }

    @Override
    protected Product createTargetProduct() throws Exception {
        final Map<String, Product> productMap = new HashMap<String, Product>(5);
        productMap.put("master", form.getMasterProduct());
        productMap.put("slave", form.getSlaveProduct());

        return GPF.createProduct(OperatorSpi.getOperatorAlias(CollocateOp.class), parameterSupport.getParameterMap(),
                                 productMap);
    }

    @Override
    public int show() {
        form.prepareShow();
        setContent(form);
        return super.show();
    }

    @Override
    public void hide() {
        form.prepareHide();
        super.hide();
    }
}
