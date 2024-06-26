/*
 * Copyright 2022 Adaptive Financial Consulting Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.artio.engine.logger;

import org.agrona.DirectBuffer;
import uk.co.real_logic.artio.messages.FixMessageDecoder;

import static org.junit.Assert.assertEquals;

public final class FixMessageConsumerValidator
{
    public static String validateFixMessageConsumer(
        final FixMessageDecoder message,
        final DirectBuffer buffer,
        final int offset,
        final int length)
    {
        assertEquals(message.bodyLength(), length);

        final String body = message.body();

        assertEquals(body, buffer.getStringWithoutLengthUtf8(offset, length));
        return body;
    }
}
