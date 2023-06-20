package io.github.dddplus.ast.view;

import com.google.common.collect.Sets;
import io.github.dddplus.ast.ReverseEngineeringModel;
import io.github.dddplus.ast.model.*;
import io.github.dddplus.ast.report.ClassMethodReport;
import io.github.dddplus.dsl.KeyElement;
import io.github.dddplus.dsl.KeyRelation;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.*;

/**
 * DSL -> Reverse Engineering Model -> PlantUML DSL.
 */
public class PlantUmlBuilder {
    public enum Direction {
        TopToBottom,
        LeftToRight,
    }

    private static final String STARTUML = "@startuml";
    private static final String ENDUML = "@enduml";
    private static final String SEMICOLON = ":";
    private static final String COMMA = ",";
    private static final String BRACE_OPEN = "{";
    private static final String BRACE_CLOSE = "}";
    private static final String STEREOTYPE_OPEN = "<<";
    private static final String STEREOTYPE_CLOSE = ">>";
    private static final String SPACE = " ";
    private static final String TAB = SPACE + SPACE;
    private static final String NEWLINE = System.getProperty("line.separator");
    private static final String QUOTE = "\"";
    private static final String HASHTAG = "#";
    private static final String PACKAGE_TMPL = "package {0} <<{1}>>";
    private static final String BRACKET_OPEN = "(";
    private static final String BRACKET_CLOSE = ")";
    private static final String DIRECTION_TOP_BOTTOM = "left to right direction";
    private static final String DIRECTION_LEFT_RIGHT = "top to bottom direction";

    private final Map<KeyRelation.Type, String> connections;
    private Set<KeyElement.Type> ignored;
    private ReverseEngineeringModel model;
    private final StringBuilder content = new StringBuilder();
    private String header;
    private String footer = "generated by DDDplus";
    private String title;
    private Direction direction;
    private Set<String> skinParams = new HashSet<>();

    public PlantUmlBuilder() {
        connections = new HashMap<>();
        connections.put(KeyRelation.Type.Union, "x--x");

        connections.put(KeyRelation.Type.HasOne, escape("1") + " *-- " + escape("1"));
        connections.put(KeyRelation.Type.HasMany, escape("1") + " *-- " + escape("N"));

        connections.put(KeyRelation.Type.Many2Many, "--");
        connections.put(KeyRelation.Type.Contextual, "--|>");
        connections.put(KeyRelation.Type.NotifiedBy, "--o");
        connections.put(KeyRelation.Type.From, "-->");
        connections.put(KeyRelation.Type.Extends, "--|>");
        connections.put(KeyRelation.Type.Implements, "---|>");
    }

    public String umlContent() {
        if (model == null) {
            throw new IllegalArgumentException("call build before this");
        }

        return content.toString();
    }

    public PlantUmlBuilder build(ReverseEngineeringModel model) {
        return build(model, Sets.newHashSet());
    }

    public PlantUmlBuilder build(ReverseEngineeringModel model, Set<KeyElement.Type> ignored) {
        this.model = model;
        this.ignored = ignored;

        start().appendDirection().appendSkinParam().appendTitle().appendHeader();

        addClassMethodReport();

        model.aggregates().forEach(a -> addAggregate(a));
        //addSimilarities();
        addKeyUsecases();
        addOrphanKeyFlows();
        addKeyRelations();

        appendFooter().end();
        return this;
    }

    public void renderSvg(String svgFilename) throws IOException {
        SourceStringReader reader = new SourceStringReader(content.toString());
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        String desc = reader.generateImage(os, new FileFormatOption(FileFormat.SVG));
        try (OutputStream outputStream = new FileOutputStream(svgFilename)) {
            os.writeTo(outputStream);
            os.close();
        }
    }

    private PlantUmlBuilder addClassMethodReport() {
        ClassMethodReport report = model.getClassMethodReport();
        append("note as ClassMethodReportNote").append(NEWLINE);
        append(String.format("  Class: annotated(%d) public(%d) deprecated(%d)",
                model.annotatedModels(),
                report.getClassInfo().getPublicClasses().size(),
                report.getClassInfo().getDeprecatedClasses().size()
                )).append(NEWLINE);
        append(String.format("  Method: annotated(%d) public(%d) default(%d) private(%d) protected(%d) static(%d) deprecated(%d)",
                model.annotatedMethods(),
                report.getMethodInfo().getPublicMethods().size(),
                report.getMethodInfo().getDefaultMethods().size(),
                report.getMethodInfo().getPrivateMethods().size(),
                report.getMethodInfo().getProtectedMethods().size(),
                report.getMethodInfo().getStaticMethods().size(),
                report.getMethodInfo().getDeprecatedMethods().size()
                )).append(NEWLINE);
        append("end note").append(NEWLINE).append(NEWLINE);
        return this;
    }

    private PlantUmlBuilder start() {
        content.append(STARTUML).append(NEWLINE).append(NEWLINE);
        return this;
    }

    private PlantUmlBuilder end() {
        content.append(NEWLINE).append(ENDUML);
        return this;
    }

    private String escape(String value) {
        return QUOTE + value + QUOTE;
    }

    private String color(String color) {
        return HASHTAG + color;
    }

    private PlantUmlBuilder writeOrphanFlowClazzDefinition(String actor) {
        if (model.getKeyModelReport().containsActor(actor)) {
            return this;
        }

        List<KeyFlowEntry> orphanFlowsOfActor = model.getKeyFlowReport().orphanFlowsOfActor(actor);
        if (orphanFlowsOfActor.isEmpty()) {
            return this;
        }

        content.append("class ").append(actor);
        content.append(" {").append(NEWLINE);
        for (KeyFlowEntry entry : orphanFlowsOfActor) {
            content.append("    {method} ");
            content.append(entry.displayNameWithRemark())
                    .append(BRACKET_OPEN)
                    .append(entry.displayArgsWithRules())
                    .append(BRACKET_CLOSE)
                    .append(SPACE)
                    .append(entry.getJavadoc())
                    .append(NEWLINE);
        }
        content.append(TAB).append("}").append(NEWLINE);
        return this;
    }

    private PlantUmlBuilder writeKeyUsecaseClazzDefinition(String actor) {
        content.append("class ").append(actor);
        content.append(" {").append(NEWLINE);
        for (KeyUsecaseEntry entry : model.getKeyUsecaseReport().actorKeyUsecases(actor)) {
            content.append("    {method} ");
            if (!entry.displayOut().isEmpty()) {
                content.append(entry.displayOut()).append(SPACE);
                content.append(entry.displayNameWithRemark())
                        .append(BRACKET_OPEN)
                        .append(entry.displayIn())
                        .append(SPACE)
                        .append(BRACKET_CLOSE)
                        .append(NEWLINE);
            } else {
                content.append(entry.displayNameWithRemark())
                        .append(BRACKET_OPEN)
                        .append(entry.displayIn())
                        .append(SPACE)
                        .append(BRACKET_CLOSE)
                        .append(NEWLINE);
            }
        }
        content.append(TAB).append("}").append(NEWLINE);
        return this;
    }

    private PlantUmlBuilder writeClazzDefinition(KeyModelEntry keyModelEntry, boolean isAggregateRoot) {
        content.append("class ").append(keyModelEntry.getClassName());
        if (isAggregateRoot) {
            if (keyModelEntry.hasJavadoc()) {
                content.append(String.format(" <<(R,#FF7700) %s>> ", keyModelEntry.getJavadoc()));
            } else {
                content.append(String.format(" <<(R,#FF7700)>> "));
            }
        } else if (keyModelEntry.isBehaviorOnly()) {
            if (keyModelEntry.hasJavadoc()) {
                content.append(String.format(" <<(B,#9197DB) %s>> ", keyModelEntry.getJavadoc()));
            } else {
                content.append(String.format(" <<(B,#9197DB)>> "));
            }
        } else {
            if (keyModelEntry.hasJavadoc()) {
                content.append(String.format(" <<%s>> ", keyModelEntry.getJavadoc()));
            }
        }
        content.append(" {").append(NEWLINE);
        if (!keyModelEntry.types().isEmpty()) {
            for (KeyElement.Type type : keyModelEntry.types()) {
                if (ignored.contains(type)) {
                    continue;
                }

                content.append(String.format("    __ %s __", type)).append(NEWLINE);
                content.append("    {field} ").append(keyModelEntry.displayFieldByType(type)).append(NEWLINE);
            }

            content.append("    __ undefined __").append(NEWLINE);
            content.append("    {field} ").append(keyModelEntry.displayUndefinedTypes()).append(NEWLINE);
        }

        if (!keyModelEntry.getKeyRuleEntries().isEmpty()) {
            content.append("    __ 规则 __").append(NEWLINE);
            for (KeyRuleEntry entry : keyModelEntry.getKeyRuleEntries()) {
                content.append("    {method} ");
                content.append(entry.displayNameWithRemark())
                        .append(BRACKET_OPEN)
                        .append(entry.displayRefer())
                        .append(BRACKET_CLOSE)
                        .append(SPACE)
                        .append(entry.getJavadoc())
                        .append(NEWLINE);
            }
        }

        if (!keyModelEntry.getKeyBehaviorEntries().isEmpty()) {
            content.append("    __ 行为 __").append(NEWLINE);
            for (KeyBehaviorEntry entry : keyModelEntry.getKeyBehaviorEntries()) {
                content.append("    {method} ");
                content.append(entry.displayNameWithRemark())
                        .append(BRACKET_OPEN)
                        .append(entry.displayArgs())
                        .append(BRACKET_CLOSE)
                        .append(SPACE)
                        .append(entry.getJavadoc())
                        .append(NEWLINE);
            }
        }

        if (!keyModelEntry.getKeyFlowEntries().isEmpty()) {
            content.append("    __ 流程 __").append(NEWLINE);
            for (KeyFlowEntry entry : keyModelEntry.getKeyFlowEntries()) {
                content.append("    {method} ");
                content.append(entry.getMethodName())
                        .append(BRACKET_OPEN)
                        .append(entry.displayArgsWithRules())
                        .append(BRACKET_CLOSE)
                        .append(SPACE)
                        .append(entry.getJavadoc())
                        .append(SPACE)
                        .append(entry.displayActualClass())
                        .append(NEWLINE);
            }
        }

        content.append(TAB).append("}").append(NEWLINE);
        // the note
        if (false && keyModelEntry.hasJavadoc()) {
            content.append("note left: " + keyModelEntry.getJavadoc()).append(NEWLINE);
        }

        return this;
    }

    private PlantUmlBuilder append(String s) {
        if (s != null) {
            content.append(s);
        }
        return this;
    }

    private PlantUmlBuilder appendHeader() {
        if (header != null && !header.isEmpty()) {
            content.append("header").append(NEWLINE).append(header).append(NEWLINE)
                    .append("endheader").append(NEWLINE).append(NEWLINE);
        }
        return this;
    }

    private PlantUmlBuilder appendSkinParam() {
        if (!skinParams.isEmpty()) {
            for (String skin : skinParams) {
                append("skinparam").append(SPACE).append(skin).append(NEWLINE);
            }
            append(NEWLINE);
        }
        return this;
    }

    private PlantUmlBuilder appendDirection() {
        if (direction != null) {
            switch (direction) {
                case LeftToRight:
                    append(DIRECTION_LEFT_RIGHT).append(NEWLINE);
                    break;
                case TopToBottom:
                    append(DIRECTION_TOP_BOTTOM).append(NEWLINE);
                    break;
            }
            append(NEWLINE);
        }
        return this;
    }

    private PlantUmlBuilder appendTitle() {
        if (title != null && !title.isEmpty()) {
            content.append("title").append(SPACE).append(title)
                    .append(NEWLINE).append(NEWLINE);
        }
        return this;
    }

    private PlantUmlBuilder appendFooter() {
        if (footer != null && !footer.isEmpty()) {
            content.append("footer").append(NEWLINE).append(footer).append(NEWLINE)
                    .append("endfooter").append(NEWLINE).append(NEWLINE);
        }
        return this;
    }

    private PlantUmlBuilder addAggregate(AggregateEntry aggregate) {
        content.append(MessageFormat.format(PACKAGE_TMPL, "Aggregate：" + aggregate.getName(), aggregate.getPackageName()));
        content.append(SPACE).append(BRACE_OPEN).append(NEWLINE);
        for (KeyModelEntry clazz : aggregate.keyModels()) {
            append(TAB).writeClazzDefinition(clazz, aggregate.isRoot(clazz)).append(NEWLINE);
        }
        content.append(BRACE_CLOSE);
        content.append(NEWLINE).append(NEWLINE);

        return this;
    }

    private PlantUmlBuilder addSimilarities() {
        content.append(MessageFormat.format(PACKAGE_TMPL, "相似度", "%"));
        content.append(SPACE).append(BRACE_OPEN).append(NEWLINE);
        for (SimilarityEntry entry : model.sortedSimilarities()) {
            append(TAB).append(entry.getLeftClass()).append(" .. ").append(entry.getRightClass())
                    .append(": ").append(String.format("%.0f", entry.getSimilarity()))
                    .append(NEWLINE);
        }
        content.append(BRACE_CLOSE);
        content.append(NEWLINE).append(NEWLINE);

        return this;
    }

    private PlantUmlBuilder addKeyRelations() {
        for (KeyRelationEntry entry : model.getKeyRelationReport().getRelationEntries()) {
            append(entry.getLeftClass())
                    .append(SPACE).append(connections.get(entry.getType())).append(SPACE)
                    .append(entry.getRightClass())
                    .append(": ").append(entry.getType().toString())
                    .append(SPACE).append(entry.displayRemark()).append(NEWLINE);
        }
        content.append(NEWLINE);
        return this;
    }

    private PlantUmlBuilder addOrphanKeyFlows() {
        if (model.getKeyFlowReport().actors().isEmpty()) {
            return this;
        }

        content.append(MessageFormat.format(PACKAGE_TMPL, "跨聚合复杂流程", "flows"));
        content.append(SPACE).append(BRACE_OPEN).append(NEWLINE);
        for (String actor : model.getKeyFlowReport().actors()) {
            append(TAB).writeOrphanFlowClazzDefinition(actor).append(NEWLINE);
        }

        content.append(BRACE_CLOSE);
        content.append(NEWLINE).append(NEWLINE);

        return this;
    }

    private PlantUmlBuilder addKeyUsecases() {
        if (model.getKeyUsecaseReport().getData().isEmpty()) {
            return this;
        }

        content.append(MessageFormat.format(PACKAGE_TMPL, "用例", "UseCase"));
        content.append(SPACE).append(BRACE_OPEN).append(NEWLINE);
        for (String actor : model.getKeyUsecaseReport().getData().keySet()) {
            append(TAB).writeKeyUsecaseClazzDefinition(actor).append(NEWLINE);
        }

        content.append(BRACE_CLOSE);
        content.append(NEWLINE).append(NEWLINE);
        return this;
    }

    public PlantUmlBuilder header(String header) {
        this.header = header;
        return this;
    }

    public PlantUmlBuilder footer(String footer) {
        this.footer = footer;
        return this;
    }

    public PlantUmlBuilder direction(Direction direction) {
        this.direction = direction;
        return this;
    }

    public PlantUmlBuilder skinParam(String skinParam) {
        this.skinParams.add(skinParam);
        return this;
    }

    public PlantUmlBuilder title(String title) {
        this.title = title;
        return this;
    }

}