package compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompileMain {
    String compilation;
    enum CompileMode {PRODUCT, DEBUG_COMPILE_ERROR, DEBUG_RUNTIME_ERROR, DEBUG_CONCEPTUAL_ERROR}
    CompileMode mode = CompileMode.DEBUG_COMPILE_ERROR;
    public static final String[] codeBlockKeywords = new String[]{"if", "range", "try", "while", "with", "define", "block"};
    HashMap<String, String> functionsWithOutput = new HashMap<>();
    HashMap<String, String> functionsNoOutput = new HashMap<>();
    HashMap<String, Integer> functionCalls = new HashMap<>();
    HashSet<String> functionNoReturnData = new HashSet<>();

    public void setCompilationText(String input) {
        compilation = input;
    }
    public String compile() {
        if (mode == CompileMode.PRODUCT || mode == CompileMode.DEBUG_COMPILE_ERROR) stripComments();
        addCurlyBraces();
        if (mode == CompileMode.DEBUG_CONCEPTUAL_ERROR)  {
            fixFunctions();
            fixFunctionCalls();
        }
        if (mode == CompileMode.PRODUCT || mode == CompileMode.DEBUG_RUNTIME_ERROR || mode == CompileMode.DEBUG_COMPILE_ERROR) removePrintCode();
        if (mode == CompileMode.PRODUCT) removeAllWhitespace();
        return compilation;
    }
    public void fixFunctions() {
        checkIllegalWords();
        getFunctions();
        addOutputDeclaration();
        replacePrintOutputString();
        addReturnStatementIfMissing();
    }
    public void fixFunctionCalls() {
        fixPrintInBody();
        getFunctionCallLines();
        addFunctionOutputLines();
        fixReturnStatement();
        reAddFunctionDeclarations();
    }
    public void fixPrintInBody() {
        compilation = compilation.replaceAll("\\{\\{print (.*?)}}", "\\{\\{\\$output = joinStr \"\\\\n\" \\$output $1}}");
    }
    public void getFunctionCallLines() {
        String pattern = "\\{\\{(.*?)execTemplate \"(\\w+?)\"( \\(.*?\\))?(.*?)}}";
        int[] indexes;
        int indexStart = 0;
        while ((indexes = regexIndexes(compilation.substring(indexStart), pattern))[0] != -1) {
            String function = compilation.substring(indexStart + indexes[0], indexStart + indexes[1]);
            indexStart += indexes[0] + 1;
            functionCalls.put(function, indexes[0]);
        }
    }
    public void stripComments() {
        compilation = compilation.replaceAll("/\\*.*\\*/", "");
    }
    public void addCurlyBraces() {
        String[] lines = compilation.split("\\n");
        for (int i=0;i<lines.length;i++) {
            if (!lines[i].matches("\\s*")) {
                lines[i] = lines[i].replaceAll("^(\\s*)(.*?)\\s*$", "$1{{$2}}");
            }
        }
        StringBuilder newCompilation = new StringBuilder();
        for (String line:lines) {
            newCompilation.append(line);
            newCompilation.append("\n");
        }
        compilation = newCompilation.substring(0, newCompilation.length());
    }
    public void removeAllWhitespace() {
        compilation = compilation.replaceAll("}}\\s*\\{\\{", "}}{{");
        compilation = compilation.replaceAll("\\s*$","");
    }
    public void removePrintCode() {
        compilation = compilation.replaceAll("\\{\\{print (.*?)}}", "{{$1}}");
    }

    public void getFunctions() {
        ArrayList<Integer> functionIndexes = new ArrayList<>();
        ArrayList<Integer> endIndexes = new ArrayList<>();
        int index = 0;
        while (index != -1) {
            index = compilation.indexOf("{{define", index);
            if (index != -1) {
                functionIndexes.add(index);
                index++;
            }
        }
        for (Integer startIndex : functionIndexes) {
            String name = compilation.substring(startIndex).split("\\{\\{define \"")[1].split("\"}}")[0];
            int endIndex = getFunctionEnd(startIndex + 1, compilation);
            if (compilation.substring(startIndex, endIndex).contains("{{print")) {
                functionsWithOutput.put(name, compilation.substring(startIndex, endIndex));
            } else {
                functionsNoOutput.put(name, compilation.substring(startIndex, endIndex));
            }
            if (compilation.substring(startIndex, endIndex).contains("{{return}}") || !compilation.substring(startIndex, endIndex).contains("{{return")) {
                functionNoReturnData.add(name);
            }
            endIndexes.add(endIndex);
        }
        for (int i=functionIndexes.size()-1;i>=0;i--) {
            compilation = compilation.substring(0, functionIndexes.get(i)) + compilation.substring(endIndexes.get(i));
        }
    }
    public int getFunctionEnd(int startIndex, String text) {
        String toCheck = text.substring(startIndex);
        while (true) {
            int minIndexBlockKeyword = Integer.MAX_VALUE;
            for (String keyword: codeBlockKeywords) {
                int index = toCheck.indexOf("{{" + keyword);
                if (index != -1 && index < minIndexBlockKeyword) minIndexBlockKeyword = index;
            }
            int endIndex = toCheck.indexOf("{{end}}");
            if (endIndex == -1) throw new IllegalStateException("No end to the function");
            if (endIndex < minIndexBlockKeyword) {
                return startIndex + endIndex + 7;
            }
            int newEnd = getFunctionEnd(startIndex + minIndexBlockKeyword + 1, text);
            startIndex = newEnd;
            toCheck = text.substring(startIndex);
        }
    }
    public void addOutputDeclaration() {
        compilation = "\n{{$output := \"\"}}" + compilation;
        for (String key: functionsWithOutput.keySet()) {
            String function = functionsWithOutput.get(key);
            function = function.replaceFirst("}}", "}}\n    \\{\\{\\$output := \"\"}}");
            functionsWithOutput.put(key, function);
        }
        compilation += "{{$output}}";

    }
    public void replacePrintOutputString() {
        for (String key: functionsWithOutput.keySet()) {
            String function = functionsWithOutput.get(key);
            function = function.replaceAll("\\{\\{print (.*?)}}", "\\{\\{\\$output = joinStr \"\\\\n\" \\$output $1}}");
            functionsWithOutput.put(key, function);
        }
    }
    public void reAddFunctionDeclarations() {
        for (String key: functionsNoOutput.keySet()) {
            compilation = functionsNoOutput.get(key) + "\n" + compilation;
        }
        for (String key: functionsWithOutput.keySet()) {
            compilation = functionsWithOutput.get(key) + "\n" + compilation;
        }

    }
    public void addFunctionOutputLines() {
        for (String functionCall : functionCalls.keySet()) {
            String name = functionCall.replaceAll("\\{\\{.*?execTemplate \"(\\w+?)\".*}}", "$1");
            int startIndex = functionCalls.get(functionCall);
            if (!functionsNoOutput.containsKey(name)) {
                if (functionNoReturnData.contains(name)) {
                    compilation = compilation.substring(0, startIndex) + compilation.substring(startIndex, startIndex + functionCall.length()).replaceAll("\\{\\{(.*?)execTemplate \"(\\w+)\"( \\(.*?\\))?(.*?)}}", "\\{\\{\\$output = joinStr \"\\\\n\" \\$output (execTemplate \"$2\"$3)}}") + compilation.substring(startIndex + functionCall.length());
                } else {
                    compilation = compilation.substring(0, startIndex) + compilation.substring(startIndex, startIndex + functionCall.length()).replaceAll("\\{\\{(.*?)execTemplate \"(\\w+)\"( \\(.*?\\))?(.*?)}}", "\\{\\{\\$$2Output := execTemplate \"$2\"$3}}\n\\{\\{\\$output = joinStr \"\\\\n\" \\$output (index \\$$2Output 0)}}\n\\{\\{$1(index \\$$2Output 1)$4}}") + compilation.substring(startIndex + functionCall.length());
                }
            }
        }
    }
    public void addReturnStatementIfMissing() {
        for (String key: functionsWithOutput.keySet()) {
            String function = functionsWithOutput.get(key);
            if (function.indexOf("{{return") == -1) {
                function = function.replaceAll("(\\n\\{\\{end}})","\n    {{return}}$1");
            }
            functionsWithOutput.put(key, function);
        }
    }
    public void fixReturnStatement() {
        for (String function: functionsWithOutput.keySet()) {
            functionsWithOutput.put(function, functionsWithOutput.get(function).replaceAll("\\{\\{return (.*?)}}", "\\{\\{return (cslice \\$output ($1))}}"));
            functionsWithOutput.put(function, functionsWithOutput.get(function).replaceAll("\\{\\{return}}", "\\{\\{return \\$output}}"));
            if (!functionsWithOutput.get(function).contains("{{return")) {
                functionsWithOutput.put(function, functionsWithOutput.get(function).replaceAll("\\n\\{\\{end}}","\\n    {{return \\$output}}\\n{{end}}"));
            }
        }
    }

    private static String regexSubstring(String source, String pattern) {
        int[] indexes = regexIndexes(source, pattern);
        if (indexes[0] == -1 || indexes[1] == -1) {
            return null;
        }
        return source.substring(indexes[0], indexes[1]);
    }
    private static int[] regexIndexes(String source, String pattern) {
        int start = -1;
        int end = -1;
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(source);
        if (m.find()) {
            start = m.start();
            end = m.end();
        }
        return new int[]{start, end};
    }
    private static String[] getRegexGroups(String source, String pattern) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(source);
        String[] toReturn = new String[m.groupCount()-1];
        if (m.matches()) {
            for (int i = 0; i < m.groupCount()-1; i++) {
                toReturn[i] = m.group(i+1);
            }
        }
        return toReturn;
    }
    private void checkIllegalWords() {
        if (compilation.split("\\$\\w*?[oO]utput").length != 1) throw new IllegalArgumentException("Variable must not contain the word output");
    }
}
