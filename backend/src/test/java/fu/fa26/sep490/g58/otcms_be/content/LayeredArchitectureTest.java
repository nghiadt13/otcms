package fu.fa26.sep490.g58.otcms_be.content;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.GenericArrayType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** No architecture library: reflection and class/source inspection enforce ARCH-01. */
class LayeredArchitectureTest {
    private static final String ROOT = "fu.fa26.sep490.g58.otcms_be.";
    private static final List<String> FEATURES = List.of("content", "activitylog", "reporting");
    @Test void controllersDependOnServicesAndNeverPersistenceOrEntities() throws Exception {
        for (String feature : FEATURES) {
            for (Class<?> type : types(feature, "controller")) {
                for (var constructor : type.getDeclaredConstructors()) {
                    for (var dependency : constructor.getParameterTypes()) assertNotPersistence(dependency);
                }
                for (var field : type.getDeclaredFields()) assertNotPersistence(field.getType());
                for (var method : type.getDeclaredMethods()) {
                    if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) assertDto(method.getGenericReturnType(), new HashSet<>());
                }
            }
        }
    }
    @Test void serviceBytecodeAndImportsNeverReferenceControllersOrSpringWeb() throws Exception {
        for (String feature : FEATURES) {
            for (Class<?> type : types(feature, "service")) {
                try (var resource = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                    assertNotNull(resource);
                    String constants = new String(resource.readAllBytes(), StandardCharsets.ISO_8859_1);
                    assertFalse(constants.contains("/controller/"), type.getName());
                    assertFalse(constants.contains("org/springframework/web/"), type.getName());
                    assertFalse(constants.contains("org/springframework/http/"), type.getName());
                    String source = Files.readString(Path.of("src/main/java", type.getName().replace('.', '/') + ".java"));
                    assertFalse(java.util.regex.Pattern.compile("(?m)^import .*\\.controller\\.").matcher(source).find(), type.getName());
                }
            }
            Path source = Path.of("src/main/java", ROOT.replace('.', '/'), feature);
            for (String obsolete : List.of("adapter", "application", "domain")) assertFalse(Files.exists(source.resolve(obsolete)));
        }
    }
    @Test void ownedCrudEntitiesUseJpaAndMutableRecordsHaveVersions() throws Exception {
        for (Class<?> type : types("content", "model/entity")) assertTrue(type.isAnnotationPresent(jakarta.persistence.Entity.class));
        for (String name : List.of("WebsiteContentJpaEntity")) {
            Class<?> type = Class.forName(ROOT + "content.model.entity." + name);
            assertTrue(type.getDeclaredField("version").isAnnotationPresent(jakarta.persistence.Version.class));
        }
        for (String name : List.of("WebsiteContentRepository", "WebsiteImageRepository")) {
            assertTrue(org.springframework.data.jpa.repository.JpaRepository.class.isAssignableFrom(Class.forName(ROOT + "content.repository." + name)));
        }
    }
    private List<Class<?>> types(String feature, String folder) throws Exception {
        Path base = Path.of("src/main/java", ROOT.replace('.', '/'), feature, folder);
        try (var paths = Files.walk(base)) {
            var sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
            var result = new java.util.ArrayList<Class<?>>();
            for (Path source : sources) {
                String relative = Path.of("src/main/java").relativize(source).toString();
                result.add(Class.forName(relative.substring(0, relative.length() - 5).replace('\\', '.').replace('/', '.')));
            }
            return result;
        }
    }
    private void assertNotPersistence(Class<?> type) {
        assertFalse(type.getName().contains(".repository."), type.getName());
        assertFalse(type.getName().contains(".model.entity."), type.getName());
        assertFalse(jakarta.persistence.EntityManager.class.isAssignableFrom(type), type.getName());
    }
    private void assertDto(Type type, Set<Type> visited) {
        if (!visited.add(type)) return;
        if (type instanceof ParameterizedType parameterized) {
            assertDto(parameterized.getRawType(), visited);
            for (Type argument : parameterized.getActualTypeArguments()) assertDto(argument, visited);
        }
        else if (type instanceof Class<?> raw) {
            assertFalse(Map.class.isAssignableFrom(raw), raw.getName());
            assertNotEquals(Object.class, raw);
            assertFalse(raw.isAnnotationPresent(jakarta.persistence.Entity.class), raw.getName());
            if (raw.getName().startsWith(ROOT) && raw.isSealed()) {
                for (Class<?> subtype : raw.getPermittedSubclasses()) assertDto(subtype, visited);
            }
            if (raw.getName().startsWith(ROOT) && raw.isRecord()) {
                for (var component : raw.getRecordComponents()) assertDto(component.getGenericType(), visited);
            }
        }
    }
}
