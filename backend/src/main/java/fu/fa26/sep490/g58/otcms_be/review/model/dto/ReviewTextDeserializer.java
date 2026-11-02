package fu.fa26.sep490.g58.otcms_be.review.model.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/** Scoped to review request fields; null/missing values remain application validation errors. */
public class ReviewTextDeserializer extends StdDeserializer<String> {
    public ReviewTextDeserializer() { super(String.class); }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_STRING))
            return context.reportInputMismatch(String.class, "Review text must be a JSON string");
        return parser.getString();
    }
}
