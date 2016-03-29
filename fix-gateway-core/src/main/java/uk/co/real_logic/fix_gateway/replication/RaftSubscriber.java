/*
 * Copyright 2015-2016 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.fix_gateway.replication;

import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import uk.co.real_logic.fix_gateway.messages.*;

import static uk.co.real_logic.fix_gateway.messages.MessageHeaderDecoder.ENCODED_LENGTH;
import static uk.co.real_logic.fix_gateway.messages.ResendDecoder.bodyHeaderLength;

public class RaftSubscriber implements FragmentHandler
{

    private final MessageHeaderDecoder messageHeader = new MessageHeaderDecoder();
    private final MessageAcknowledgementDecoder messageAcknowledgement = new MessageAcknowledgementDecoder();
    private final RequestVoteDecoder requestVote = new RequestVoteDecoder();
    private final ReplyVoteDecoder replyVote = new ReplyVoteDecoder();
    private final ConcensusHeartbeatDecoder concensusHeartbeat = new ConcensusHeartbeatDecoder();
    private final ResendDecoder resend = new ResendDecoder();

    private final RaftHandler handler;

    public RaftSubscriber(final RaftHandler handler)
    {
        this.handler = handler;
    }

    public void onFragment(final DirectBuffer buffer, int offset, final int length, final Header header)
    {
        messageHeader.wrap(buffer, offset);

        final int blockLength = messageHeader.blockLength();
        final int version = messageHeader.version();
        offset += messageHeader.encodedLength();

        switch (messageHeader.templateId())
        {
            case MessageAcknowledgementDecoder.TEMPLATE_ID:
            {
                messageAcknowledgement.wrap(buffer, offset, blockLength, version);
                handler.onMessageAcknowledgement(
                    messageAcknowledgement.newAckedPosition(),
                    messageAcknowledgement.nodeId(),
                    messageAcknowledgement.status()
                );
                return;
            }

            case RequestVoteDecoder.TEMPLATE_ID:
            {
                requestVote.wrap(buffer, offset, blockLength, version);
                handler.onRequestVote(
                    requestVote.candidateId(),
                    requestVote.candidateSessionId(),
                    requestVote.leaderShipTerm(),
                    requestVote.lastAckedPosition()
                );
                return;
            }

            case ReplyVoteDecoder.TEMPLATE_ID:
            {
                replyVote.wrap(buffer, offset, blockLength, version);
                handler.onReplyVote(
                    replyVote.senderNodeId(),
                    replyVote.candidateId(),
                    replyVote.leaderShipTerm(),
                    replyVote.vote()
                );
                return;
            }

            case ConcensusHeartbeatDecoder.TEMPLATE_ID:
            {
                concensusHeartbeat.wrap(buffer, offset, blockLength, version);
                handler.onConcensusHeartbeat(
                    concensusHeartbeat.nodeId(),
                    concensusHeartbeat.leaderShipTerm(),
                    concensusHeartbeat.position(),
                    concensusHeartbeat.leaderSessionId());
                return;
            }

            case ResendDecoder.TEMPLATE_ID:
            {
                resend.wrap(buffer, offset, blockLength, version);
                final int bodyOffset = offset + ENCODED_LENGTH + blockLength + bodyHeaderLength();
                handler.onResend(
                    resend.leaderSessionId(),
                    resend.leaderShipTerm(),
                    resend.startPosition(),
                    buffer,
                    bodyOffset,
                    resend.bodyLength()
                );
                return;
            }
        }
    }
}
