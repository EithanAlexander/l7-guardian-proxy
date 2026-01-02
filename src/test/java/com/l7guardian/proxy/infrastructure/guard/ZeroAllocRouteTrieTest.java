package com.l7guardian.proxy.infrastructure.guard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZeroAllocRouteTrieTest {

    private ZeroAllocRouteTrie trie;

    @BeforeEach
    void setUp() {
        trie = new ZeroAllocRouteTrie();
    }

    @Test
    @DisplayName("Should allow exact matches for whitelisted paths")
    void shouldAllowExactMatch() {
        trie.insert("/api/v1");
        assertTrue(trie.isAllowed("/api/v1"), "Should allow exact match");
    }

    @Test
    @DisplayName("Should allow children of whitelisted prefixes - Prefix Matching")
    void shouldAllowPrefixChildren() {
        trie.insert("/api/v1");

        // Since /api/v1 is allowed, anything UNDER it should be allowed
        assertTrue(trie.isAllowed("/api/v1/users"), "Should allow sub-resource");
        assertTrue(trie.isAllowed("/api/v1/products/123/details"), "Should allow deep sub-resource");
    }

    @Test
    @DisplayName("SECURITY: Should BLOCK partial segment matches")
    void shouldBlockPartialSegmentMatches() {
        trie.insert("/admin");

        // "/admin-dashboard" starts with "/admin", but is a different directory
        assertFalse(trie.isAllowed("/admin-dashboard"), "Should block partial segment match");
        assertFalse(trie.isAllowed("/administrator"), "Should block prefix that isn't a segment");
    }

    @Test
    @DisplayName("SECURITY: Should BLOCK parent paths if they are not explicitly whitelisted")
    void shouldBlockParentPathIfNotWhitelisted() {
        // We only whitelist the specific version, not the root
        trie.insert("/api/v1");

        assertFalse(trie.isAllowed("/api"), "Should block parent /api if only /api/v1 is allowed");
        assertFalse(trie.isAllowed("/"), "Should block root");
    }

    @Test
    @DisplayName("Should handle multiple distinct routes")
    void shouldHandleMultipleRoutes() {
        trie.insert("/api/v1");
        trie.insert("/health");
        trie.insert("/auth/login");

        assertTrue(trie.isAllowed("/api/v1/users"));
        assertTrue(trie.isAllowed("/health"));
        assertTrue(trie.isAllowed("/auth/login"));

        assertFalse(trie.isAllowed("/auth/register"), "Sibling path should be blocked if not inserted");
    }

    @Test
    @DisplayName("Edge Case: Trailing Slashes")
    void shouldHandleTrailingSlashes() {
        trie.insert("/api/v1");

        // In prefix mode, a trailing slash is just the start of a child, which is allowed
        assertTrue(trie.isAllowed("/api/v1/"));
    }

    @Test
    @DisplayName("Edge Case: Nulls and Empty Strings")
    void shouldHandleEmptyInputs() {
        assertFalse(trie.isAllowed(null));
        assertFalse(trie.isAllowed(""));
        assertFalse(trie.isAllowed("   ")); // whitespace is not a valid path
    }

    @Test
    @DisplayName("Edge Case: Root-level matches")
    void shouldHandleRootLevelPaths() {
        trie.insert("/public");

        assertTrue(trie.isAllowed("/public"));
        assertFalse(trie.isAllowed("/private"));
    }
}
