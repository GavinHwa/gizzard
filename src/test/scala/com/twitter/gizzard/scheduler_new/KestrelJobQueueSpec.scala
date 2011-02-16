package com.twitter.gizzard
package scheduler

import scala.collection.mutable
import com.twitter.util.Time
import com.twitter.util.TimeConversions._
import net.lag.kestrel.{PersistentQueue, QItem}
import net.lag.kestrel.config.QueueConfig
import org.specs.Specification
import org.specs.mock.{ClassMocker, JMocker}


object KestrelJobQueueSpec extends ConfiguredSpecification with JMocker with ClassMocker {
  "KestrelJobQueue" should {
    val queue = mock[PersistentQueue]
    val queue2 = mock[PersistentQueue]
    val codec = mock[Codec[Job]]
    val job1 = mock[Job]
    val job2 = mock[Job]
    val destinationQueue = mock[KestrelJobQueue[Job]]
    val aQueueConfig = mock[QueueConfig]


    var kestrelJobQueue: KestrelJobQueue[Job] = null

    doBefore {
      kestrelJobQueue = new KestrelJobQueue("queue", queue, codec)
    }

    "size" in {
      expect {
        one(queue).length willReturn 23L
      }

      kestrelJobQueue.size mustEqual 23
    }

    "age" in {
      expect {
        one(queue).currentAge willReturn 23500.milliseconds
      }

      kestrelJobQueue.age mustEqual 23.5
    }

    "start, pause, resume, shutdown" in {
      expect {
        one(queue).config willReturn aQueueConfig
        one(aQueueConfig).copy(maxExpireSweep = 0) willReturn aQueueConfig
        one(queue).config_=(aQueueConfig)
        one(queue).setup()
      }

      kestrelJobQueue.start()

      expect {
        one(queue).pauseReads()
      }

      kestrelJobQueue.pause()

      expect {
        one(queue).resumeReads()
      }

      kestrelJobQueue.resume()

      expect {
        one(queue).close()
      }

      kestrelJobQueue.shutdown()
    }

    "put" in {
      "success" in {
        expect {
          one(codec).flatten(job1) willReturn "abc".getBytes
          one(queue).add("abc".getBytes) willReturn true
        }

        kestrelJobQueue.put(job1)
      }

      "failure" in {
        expect {
          one(codec).flatten(job1) willReturn "abc".getBytes
          one(queue).add("abc".getBytes) willReturn false
        }

        kestrelJobQueue.put(job1) must throwA[Exception]
      }
    }

    "get" in {
      "after shutdown" in {
        expect {
          one(queue).close()
          one(queue).isClosed willReturn true
        }

        kestrelJobQueue.shutdown()
        kestrelJobQueue.get() mustEqual None
      }

      "item available immediately" in {
        expect {
          allowing(queue).isClosed willReturn false
          one(queue).removeReceive(any[Option[Time]], any[Boolean]) willReturn Some(QItem(Time.fromSeconds(0), None, "abc".getBytes, 900))
          one(codec).inflate("abc".getBytes) willReturn job1
          one(queue).confirmRemove(900)
        }

        val ticket = kestrelJobQueue.get()
        ticket must beSome[Ticket[Job]].which { _.job == job1 }
        ticket.get.ack()
      }

      "item available eventually" in {
        expect {
          allowing(queue).isClosed willReturn false
          one(queue).removeReceive(any[Option[Time]], any[Boolean]).willReturn(None) then
            one(queue).removeReceive(any[Option[Time]], any[Boolean]).willReturn(Some(QItem(Time.fromSeconds(0), None, "abc".getBytes, 900)))
          one(codec).inflate("abc".getBytes) willReturn job1
          one(queue).confirmRemove(900)
        }

        val ticket = kestrelJobQueue.get()
        ticket must beSome[Ticket[Job]].which { _.job == job1 }
        ticket.get.ack()
      }
    }

    "drainTo" in {
      expect {
        one(destinationQueue).queue willReturn queue2

        one(queue).config willReturn aQueueConfig
        one(aQueueConfig).copy(maxAge = Some(1.second)) willReturn aQueueConfig

        one(queue).expireQueue_=(Some(queue2))
        one(queue).config_=(aQueueConfig)
      }

      kestrelJobQueue.drainTo(destinationQueue, 1.second)
    }
  }
}
