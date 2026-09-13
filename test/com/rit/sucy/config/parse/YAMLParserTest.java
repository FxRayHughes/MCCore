/**
 * MCCore
 * com.rit.sucy.config.parse.YAMLParserYest
 * <p>
 * The MIT License (MIT)
 * <p>
 * Copyright (c) 2016 Steven Sucy
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.rit.sucy.config.parse;

import org.junit.Test;

import java.util.List;

public class YAMLParserTest
{

    /** 解析器按换行切分输入，用例统一用 \n 以免受平台差异影响。 */
    private static final String NL = "\n";

    @Test
    public void testBasicYAML()
    {
        DataSection data = YAMLParser.parseText("key:\n  value: 2\n  list:\n  - a\n  - 'b'" +
                                               "\n  -dashKey: {}\n  emptyList: []\n  quoted: 'text'");
        DataSection subData = data.getSection("key");
        List<String> list = subData.getList("list");

        assert subData.getInt("value") == 2;
        assert list.get(0).equals("a");
        assert list.get(1).equals("b");
        assert list.size() == 2;
        assert subData.getSection("-dashKey") != null;
        assert subData.getList("emptyList").size() == 0;
        assert subData.getString("quoted").equals("text");
    }

    @Test
    public void testInvalidYAML()
    {
        // 无冒号的行不是键，跳过即可——原实现在这里抛 IndexOutOfBoundsException，
        // 而这个解析器被用来读插件的默认配置：一处写法不合预期就会让整个插件
        // 启动失败。实测触发点是在列表项之间写注释。
        DataSection data = YAMLParser.parseText("key" + NL + "  value: 2");
        assert data != null;
        assert !data.has("key");
    }

    @Test
    public void testCommentBetweenListItems()
    {
        // 回归用例：列表项之间的注释曾让解析器停在无冒号的行上并越界。
        DataSection data = YAMLParser.parseText(
                "section:" + NL + "  list:" + NL + "  - 'a'" + NL
                        + "  # comment" + NL + "  - 'b'" + NL + "  other: 1");
        assert data != null;
        DataSection section = data.getSection("section");
        assert section != null;
        assert section.getList("list").size() == 2;
        assert section.getInt("other") == 1;
    }

    @Test
    public void testConfig() {
        // Gradle runs from the project root; the shipped fixture lives in config/.
        testFile("config/config");
    }

    private void testFile(String file)
    {
        try
        {
            DataSection data = YAMLParser.parseFile(file + ".yml");
            assert data != null;
            assert data.keys().size() > 0;
        }
        catch (Exception ex)
        {
            assert false;
        }
    }
}
