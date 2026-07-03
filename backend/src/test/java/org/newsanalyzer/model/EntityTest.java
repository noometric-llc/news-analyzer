package org.newsanalyzer.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Entity model class.
 * Tests the dual-layer design (internal type + Schema.org type) and JSONB properties.
 */
class EntityTest {

    private Entity entity;

    @BeforeEach
    void setUp() {
        entity = new Entity();
    }

    @Test
    void testEntityCreation() {
        entity.setEntityType(EntityType.PERSON);
        entity.setName("Elizabeth Warren");
        entity.setSchemaOrgType("Person");

        assertEquals(EntityType.PERSON, entity.getEntityType());
        assertEquals("Elizabeth Warren", entity.getName());
        assertEquals("Person", entity.getSchemaOrgType());
    }

    @Test
    void testPropertiesMap() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("jobTitle", "United States Senator");
        properties.put("politicalParty", "Democratic Party");

        entity.setProperties(properties);

        assertNotNull(entity.getProperties());
        assertEquals(2, entity.getProperties().size());
        assertEquals("United States Senator", entity.getProperties().get("jobTitle"));
        assertEquals("Democratic Party", entity.getProperties().get("politicalParty"));
    }

    @Test
    void testAddProperty() {
        entity.addProperty("jobTitle", "Senator");
        entity.addProperty("age", 74);

        assertEquals("Senator", entity.getProperty("jobTitle"));
        assertEquals(74, entity.getProperty("age"));
        assertEquals(2, entity.getProperties().size());
    }

    @Test
    void testGetPropertyReturnsNullForMissing() {
        assertNull(entity.getProperty("nonexistent"));
    }

    @Test
    void testSchemaOrgData() {
        Map<String, Object> schemaOrgData = new HashMap<>();
        schemaOrgData.put("@context", "https://schema.org");
        schemaOrgData.put("@type", "Person");
        schemaOrgData.put("name", "Elizabeth Warren");

        entity.setSchemaOrgData(schemaOrgData);

        assertNotNull(entity.getSchemaOrgData());
        assertEquals("https://schema.org", entity.getSchemaOrgData().get("@context"));
        assertEquals("Person", entity.getSchemaOrgData().get("@type"));
        assertEquals("Elizabeth Warren", entity.getSchemaOrgData().get("name"));
    }

    @Test
    void testAddSchemaOrgField() {
        entity.addSchemaOrgField("@context", "https://schema.org");
        entity.addSchemaOrgField("@type", "Person");
        entity.addSchemaOrgField("name", "Joe Biden");

        assertEquals(3, entity.getSchemaOrgData().size());
        assertEquals("https://schema.org", entity.getSchemaOrgData().get("@context"));
        assertEquals("Person", entity.getSchemaOrgData().get("@type"));
        assertEquals("Joe Biden", entity.getSchemaOrgData().get("name"));
    }

    @Test
    void testDefaultValues() {
        assertEquals(1.0f, entity.getConfidenceScore());
        assertFalse(entity.getVerified());
        assertNotNull(entity.getProperties());
        assertNotNull(entity.getSchemaOrgData());
    }

    @Test
    void testConfidenceScore() {
        entity.setConfidenceScore(0.85f);
        assertEquals(0.85f, entity.getConfidenceScore());
    }

    @Test
    void testVerifiedFlag() {
        assertFalse(entity.getVerified());

        entity.setVerified(true);
        assertTrue(entity.getVerified());
    }

    @Test
    void testSource() {
        entity.setSource("manual_entry");
        assertEquals("manual_entry", entity.getSource());

        entity.setSource("article:12345");
        assertEquals("article:12345", entity.getSource());
    }

    @Test
    void testAllArgsConstructor() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("jobTitle", "Senator");

        Map<String, Object> schemaOrgData = new HashMap<>();
        schemaOrgData.put("@type", "Person");

        Entity testEntity = new Entity(
            null,  // id
            EntityType.PERSON,
            "Test Person",
            properties,
            "Person",
            schemaOrgData,
            null,  // governmentOrganization (NEW in Phase 1.6)
            null,  // articleId (NEW in ES-1.1)
            null,  // article (NEW in ES-1.1)
            "test_source",
            0.95f,
            true,
            null,  // createdAt
            null   // updatedAt
        );

        assertEquals(EntityType.PERSON, testEntity.getEntityType());
        assertEquals("Test Person", testEntity.getName());
        assertEquals("Person", testEntity.getSchemaOrgType());
        assertEquals(0.95f, testEntity.getConfidenceScore());
        assertTrue(testEntity.getVerified());
        assertEquals("test_source", testEntity.getSource());
    }

    @Test
    void testPropertiesInitialization() {
        // Properties should be initialized to empty HashMap
        assertNotNull(entity.getProperties());
        assertTrue(entity.getProperties().isEmpty());
    }

    @Test
    void testSchemaOrgDataInitialization() {
        // SchemaOrgData should be initialized to empty HashMap
        assertNotNull(entity.getSchemaOrgData());
        assertTrue(entity.getSchemaOrgData().isEmpty());
    }

    @Test
    void testAddPropertyWithNullProperties() {
        entity.setProperties(null);
        entity.addProperty("test", "value");

        assertNotNull(entity.getProperties());
        assertEquals("value", entity.getProperty("test"));
    }

    @Test
    void testAddSchemaOrgFieldWithNullData() {
        entity.setSchemaOrgData(null);
        entity.addSchemaOrgField("test", "value");

        assertNotNull(entity.getSchemaOrgData());
        assertEquals("value", entity.getSchemaOrgData().get("test"));
    }

    @Test
    void testEntityTypesMapping() {
        // Test all entity types can be set
        for (EntityType type : EntityType.values()) {
            entity.setEntityType(type);
            assertEquals(type, entity.getEntityType());
        }
    }

    @Test
    void testComplexPropertyValues() {
        // Test that properties can hold complex objects
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("city", "Boston");
        nestedMap.put("state", "Massachusetts");

        entity.addProperty("location", nestedMap);

        @SuppressWarnings("unchecked")
        Map<String, Object> retrieved = (Map<String, Object>) entity.getProperty("location");
        assertEquals("Boston", retrieved.get("city"));
        assertEquals("Massachusetts", retrieved.get("state"));
    }
}
