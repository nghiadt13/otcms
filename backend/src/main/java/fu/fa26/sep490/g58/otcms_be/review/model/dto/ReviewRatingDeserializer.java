package fu.fa26.sep490.g58.otcms_be.review.model.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/** Reject decimal/string coercion: the FE contract requires a JSON integer. */
public class ReviewRatingDeserializer extends StdDeserializer<Integer> {
    public ReviewRatingDeserializer() { super(Integer.class); }
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
            return context.reportInputMismatch(Integer.class, "Rating must be an integer");
        var value = parser.getBigIntegerValue();
        if (value.compareTo(java.math.BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                || value.compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE)) > 0)
            return context.reportInputMismatch(Integer.class, "Rating is outside the integer range");
        return value.intValue();
    }
}
