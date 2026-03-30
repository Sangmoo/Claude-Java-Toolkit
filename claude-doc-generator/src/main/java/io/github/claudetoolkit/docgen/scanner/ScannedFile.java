package io.github.claudetoolkit.docgen.scanner;

/**
 * Represents a single source file found during a Spring MVC project scan.
 */
public class ScannedFile {

    private final String relativePath;
    private final String type;     // Controller, Service, ServiceImpl, Repository/DAO, Mapper, DTO/VO, MyBatis Mapper XML, SQL, Config, Java
    private final String content;

    public ScannedFile(String relativePath, String type, String content) {
        this.relativePath = relativePath;
        this.type = type;
        this.content = content;
    }

    public String getRelativePath() { return relativePath; }
    public String getType()         { return type; }
    public String getContent()      { return content; }
}
