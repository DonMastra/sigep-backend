import org.springframework.security.crypto.bcrypt.BCrypt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TeacherCredentialGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%*-_+?";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;
    private static final List<Teacher> TEACHERS = List.of(
        new Teacher("arosado", "Agustin Rosado"),
        new Teacher("amastracchio", "Andres Mastracchio"),
        new Teacher("cescobar", "Camila Escobar"),
        new Teacher("mdiaz", "Mauro Diaz"),
        new Teacher("denriquez", "Danna Enriquez"),
        new Teacher("gferreyra", "Gabriela Ferreyra"),
        new Teacher("haltamirano", "Horiana Altamirano"),
        new Teacher("omonzon", "Ornella Monzon"),
        new Teacher("pcordova", "Paola Cordova"),
        new Teacher("rmainero", "Regina Mainero")
    );

    private TeacherCredentialGenerator() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usage: <plaintext-csv> <hashes-json>");
        Path plaintextPath = Path.of(args[0]).toAbsolutePath();
        Path hashesPath = Path.of(args[1]).toAbsolutePath();
        refuseOverwrite(plaintextPath);
        refuseOverwrite(hashesPath);
        Files.createDirectories(plaintextPath.getParent());
        Files.createDirectories(hashesPath.getParent());

        List<Credential> credentials = TEACHERS.stream()
            .map(teacher -> {
                String password = password(18);
                return new Credential(teacher, password, BCrypt.hashpw(password, BCrypt.gensalt(12, RANDOM)));
            })
            .toList();

        StringBuilder csv = new StringBuilder("username,teacher_name,temporary_password,must_change_password\r\n");
        credentials.forEach(value -> csv.append(value.teacher.username).append(',')
            .append(value.teacher.name).append(',').append(value.password).append(",true\r\n"));
        Files.writeString(plaintextPath, csv, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);

        String json = credentials.stream()
            .map(value -> "  {\"username\":\"" + value.teacher.username + "\",\"passwordHash\":\"" + value.hash + "\"}")
            .collect(java.util.stream.Collectors.joining(",\n", "[\n", "\n]\n"));
        Files.writeString(hashesPath, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);

        System.out.printf("Generated %d credentials. Plaintext and BCrypt hashes were written to separate files.%n", credentials.size());
    }

    private static String password(int length) {
        List<Character> characters = new ArrayList<>();
        characters.add(randomFrom(UPPER));
        characters.add(randomFrom(LOWER));
        characters.add(randomFrom(DIGITS));
        characters.add(randomFrom(SYMBOLS));
        while (characters.size() < length) characters.add(randomFrom(ALL));
        Collections.shuffle(characters, RANDOM);
        StringBuilder result = new StringBuilder(length);
        characters.forEach(result::append);
        return result.toString();
    }

    private static char randomFrom(String alphabet) {
        return alphabet.charAt(RANDOM.nextInt(alphabet.length()));
    }

    private static void refuseOverwrite(Path path) {
        if (Files.exists(path)) throw new IllegalStateException("Refusing to overwrite " + path);
    }

    private record Teacher(String username, String name) {}
    private record Credential(Teacher teacher, String password, String hash) {}
}
