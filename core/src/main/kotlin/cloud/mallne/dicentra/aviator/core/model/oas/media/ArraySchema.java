package cloud.mallne.dicentra.aviator.core.model.oas.media;

/**
 * ArraySchema
 */

public class ArraySchema extends Schema<Object> {

    public ArraySchema() {
        super("array", null);
    }

    @Override
    public ArraySchema type(String type) {
        super.setType(type);
        return this;
    }

    @Override
    public ArraySchema items(Schema items) {
        super.setItems(items);
        return this;
    }

    @Override
    public String toString() {
        String sb = "class ArraySchema {\n" +
                "    " + toIndentedString(super.toString()) + "\n" +
                "}";
        return sb;
    }
}
