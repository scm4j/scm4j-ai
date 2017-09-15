import org.scm4j.ai.ProductStructureData;
import org.scm4j.ai.api.*;

import java.util.ArrayList;
import java.util.List;

public class ProductStructureDataLoader implements IProduct {

    public static IProductStructure getProductStructure() {
        ProductStructureData.getProductStructure();
    }
}
