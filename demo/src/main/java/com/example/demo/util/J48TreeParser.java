
package com.example.demo.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import com.fasterxml.jackson.annotation.JsonInclude;

public class J48TreeParser {

    public static TreeNode parse(String input) {
        String[] lines = input.split("\r?\n");
        return buildTree(lines);
    }

    private static TreeNode buildTree(String[] lines) {
        Stack<TreeNode> stack = new Stack<>();
        TreeNode root = null;

        for (String line : lines) {
            int depth = countIndentation(line);
            String content = line.trim();

            // Ignorar líneas vacías
            if (content.isEmpty()) continue;

            TreeNode node = new TreeNode(content);

            if (depth == 0) {
                root = node;
                stack.clear();
                stack.push(node);
            } else {
                while (stack.size() > depth) {
                    stack.pop();
                }
                TreeNode parent = stack.peek();
                parent.children.add(node);
                stack.push(node);
            }
        }

        return root;
    }

    private static int countIndentation(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == '|') count++;
            else break;
        }
        return count;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TreeNode {
        public String name;
        public List<TreeNode> children;

        public TreeNode(String name) {
            this.name = name;
            this.children = new ArrayList<>();
        }
    }
}
