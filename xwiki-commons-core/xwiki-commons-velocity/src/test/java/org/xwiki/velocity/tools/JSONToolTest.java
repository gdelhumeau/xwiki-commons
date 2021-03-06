/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.velocity.tools;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import net.sf.json.JSON;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

/**
 * Unit tests for {@link JSONTool}.
 * 
 * @version $Id$
 * @since 4.0M2
 */
public class JSONToolTest
{
    public static class MockBean
    {
        public boolean isEnabled()
        {
            return true;
        }

        public int getAge()
        {
            return 28;
        }

        public double getGrade()
        {
            return 9.48;
        }

        public String getName()
        {
            return "XWiki";
        }

        public List<String> getItems()
        {
            return Arrays.asList("one");
        }

        public Map<String, String> getParameters()
        {
            return Collections.singletonMap("foo", "bar");
        }
    }

    /**
     * The object being tested.
     */
    private JSONTool tool = new JSONTool();

    @Test
    public void testSerializeNull()
    {
        Assert.assertEquals("null", this.tool.serialize(null));
    }

    @Test
    public void testSerializeMap()
    {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("bool", false);
        map.put("int", 13);
        map.put("double", 0.78);
        map.put("string", "foo");
        map.put("array", new int[] {9, 8});
        map.put("list", Arrays.asList("one", "two"));
        map.put("map", Collections.singletonMap("level2", true));

        String json = this.tool.serialize(map);
        // We can't predict the order in the map.
        Assert.assertTrue(json.contains("\"bool\":false"));
        Assert.assertTrue(json.contains("\"int\":13"));
        Assert.assertTrue(json.contains("\"double\":0.78"));
        Assert.assertTrue(json.contains("\"string\":\"foo\""));
        Assert.assertTrue(json.contains("\"array\":[9,8]"));
        Assert.assertTrue(json.contains("\"list\":[\"one\",\"two\"]"));
        Assert.assertTrue(json.contains("\"map\":{\"level2\":true}"));
    }

    @Test
    public void testSerializeList()
    {
        Assert.assertEquals("[1,2]", this.tool.serialize(Arrays.asList(1, 2)));
        Assert.assertEquals("[1.3,2.4]", this.tool.serialize(new double[] {1.3, 2.4}));
    }

    @Test
    public void testSerializeNumber()
    {
        Assert.assertEquals("27", this.tool.serialize(27));
        Assert.assertEquals("2.7", this.tool.serialize(2.7));
    }

    @Test
    public void testSerializeBoolean()
    {
        Assert.assertEquals("false", this.tool.serialize(false));
        Assert.assertEquals("true", this.tool.serialize(true));
    }

    @Test
    public void testSerializeString()
    {
        Assert.assertEquals("\"\\\"te'st\\\"\"", this.tool.serialize("\"te'st\""));
    }

    @Test
    public void testSerializeBean()
    {
        String json = this.tool.serialize(new MockBean());
        // We can't predict the order in the map.
        Assert.assertTrue(json.contains("\"age\":28"));
        Assert.assertTrue(json.contains("\"enabled\":true"));
        Assert.assertTrue(json.contains("\"grade\":9.48"));
        Assert.assertTrue(json.contains("\"items\":[\"one\"]"));
        Assert.assertTrue(json.contains("\"name\":\"XWiki\""));
        Assert.assertTrue(json.contains("\"parameters\":{\"foo\":\"bar\"}"));
    }

    @Test
    public void testParseArray()
    {
        JSON json = this.tool.parse("[1,2,3]");
        Assert.assertTrue(json.isArray());
        Assert.assertEquals(3, json.size());
    }

    @Test
    public void testParseEmptyArray()
    {
        JSON json = this.tool.parse("[]");
        Assert.assertTrue(json.isArray());
        Assert.assertTrue(json.isEmpty());
        Assert.assertEquals(0, json.size());
    }

    @Test
    public void testParseMap()
    {
        JSONObject json = (JSONObject) this.tool.parse("{\"a\" : 1, \"b\": [1], \"c\": true}");
        Assert.assertFalse(json.isArray());
        Assert.assertFalse(json.isEmpty());
        Assert.assertEquals(3, json.size());
        Assert.assertTrue(json.getBoolean("c"));
    }

    @Test
    public void testParseEmptyMap()
    {
        JSONObject json = (JSONObject) this.tool.parse("{}");
        Assert.assertFalse(json.isArray());
        Assert.assertTrue(json.isEmpty());
        Assert.assertEquals(0, json.size());
    }

    @Test
    public void testParseNull()
    {
        Assert.assertTrue(this.tool.parse(null) instanceof JSONNull);
    }

    @Test
    public void testParseEmptyString()
    {
        Assert.assertNull(this.tool.parse(""));
    }

    @Test
    public void testParseInvalidJSON()
    {
        Assert.assertNull(this.tool.parse("This is not the JSON you are looking for..."));
    }
}
