package unimagdeportes.dto.parameter;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public class ParameterIntegerDeserializer extends StdDeserializer<Integer> {
    public ParameterIntegerDeserializer() { super(Integer.class); }

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return context.reportInputMismatch(Integer.class, "El parámetro debe ser un número entero.");
        }
        return parser.getIntValue();
    }
}
