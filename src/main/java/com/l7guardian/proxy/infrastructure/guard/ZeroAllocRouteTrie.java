package com.l7guardian.proxy.infrastructure.guard;

import java.util.ArrayList;
import java.util.List;

/**
 * A Zero-Allocation Trie for high-performance routing.
 * Optimized for "Project Loom" / Virtual Threads to minimize GC pressure.
 */
public class ZeroAllocRouteTrie {

    private final Node root = new Node("");

    private static class Node {
        String segment;      // The path part, e.g., "api"
        boolean isEnd;       // Is this a valid termination point?
        List<Node> children; // Linear list is often faster than Map for small sizes (<10)

        Node(String segment) {
            this.segment = segment;
            this.children = new ArrayList<>();
        }
    }

    /**
     * Initialization (Run once on startup).
     * Splits are fine here because it's not the hot path.
     */
    public void insert(String path) {
        String[] parts = path.split("/");
        Node current = root;

        for (String part : parts) {
            if (part.isEmpty()) continue; // Skip leading empty string from "/"

            Node match = null;
            for (Node child : current.children) {
                if (child.segment.equals(part)) {
                    match = child;
                    break;
                }
            }

            if (match == null) {
                match = new Node(part);
                current.children.add(match);
            }
            current = match;
        }
        current.isEnd = true;
    }

    /**
     * Checks whether the given request path is allowed to pass the L7 Guardian.
     * <p>
     * HOT PATH:
     * - Executed for every incoming request
     * - Zero allocations (no split, substring, regex)
     * - Segment-aware prefix matching using a pre-built trie
     *
     * <p>
     * Semantics:
     * - Default DENY
     * - Allow as soon as a configured path prefix is matched
     *
     * @param path the HTTP request URI (e.g. "/api/v1/users")
     * @return true if the path is allowed, false otherwise
     */
    public boolean isAllowed(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        int cursor = path.startsWith("/") ? 1 : 0;
        int length = path.length();
        Node current = root;

        while (cursor < length) {
            int nextSlash = path.indexOf('/', cursor);
            if (nextSlash == -1) {
                nextSlash = length;
            }

            int segmentLen = nextSlash - cursor;

            // Delegate child lookup (reduces cognitive complexity)
            Node matched = findMatchingChild(current, path, cursor, segmentLen);

            if (matched == null) {
                return false;
            }

            current = matched;

            // Prefix semantics: allow immediately on rule match
            if (current.isEnd) {
                return true;
            }

            cursor = nextSlash + 1;
        }

        return false;
    }


    /**
     * Finds a child node whose segment matches the path at the given cursor.
     * Returns null if no match is found.
     */
    private Node findMatchingChild(Node parent, String path, int cursor, int segmentLen) {
        for (int i = 0; i < parent.children.size(); i++) {
            Node child = parent.children.get(i);

            if (child.segment.length() == segmentLen &&
                    path.regionMatches(cursor, child.segment, 0, segmentLen)) {
                return child;
            }
        }
        return null;
    }
}
