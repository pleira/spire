package spire.std

import spire.algebra._
import spire.util.MapJoin

import scala.{ specialized => spec }
import scala.annotation.tailrec

@SerialVersionUID(0L)
class MapMonoid[K, V](implicit val scalar: Semigroup[V]) extends Monoid[Map[K, V]] 
with Serializable {
  def id: Map[K, V] = Map.empty

  def op(x: Map[K, V], y: Map[K, V]): Map[K, V] = {
    var xx = x
    var yy = y
    var f = scalar.op _
    if (x.size < y.size) { xx = y; yy = x; f = (x: V, y: V) => scalar.op(y, x) }
    yy.foldLeft(xx) { (z, kv) =>
      z.updated(kv._1, (xx get kv._1).map(u => f(u, kv._2)).getOrElse(kv._2))
    }
  }
}

@SerialVersionUID(0L)
class MapGroup[K, V](implicit override val scalar: Group[V]) extends MapMonoid[K, V]
with Group[Map[K, V]] with Serializable {
  def inverse(x: Map[K, V]): Map[K, V] = x.mapValues(scalar.inverse)
}

@SerialVersionUID(0L)
class MapRng[K, V](implicit val scalar: Rng[V]) extends Rng[Map[K, V]] with VectorSpace[Map[K, V], V]
with Serializable { self =>
  def zero: Map[K, V] = Map.empty

  def plus(x: Map[K, V], y: Map[K, V]): Map[K, V] = {
    var xx = x
    var yy = y
    if (x.size < y.size) { xx = y; yy = x }
    yy.foldLeft(xx) { (z, kv) =>
      z.updated(kv._1, (xx get kv._1).map(u => scalar.plus(u, kv._2)).getOrElse(kv._2))
    }
  }

  def negate(x: Map[K, V]): Map[K, V] = x mapValues (scalar.negate(_))

  def times(x: Map[K, V], y: Map[K, V]): Map[K, V] = {
    var xx = x
    var yy = y
    var f = scalar.times _
    if (x.size < y.size) { xx = y; yy = x; f = (x: V, y: V) => scalar.times(y, x) }
    yy.foldLeft(zero) { (z, kv) =>
      (xx get kv._1).map(u => z.updated(kv._1, f(u, kv._2))).getOrElse(z)
    }
  }
  
  def timesl(r: V, v: Map[K, V]): Map[K, V] = v mapValues (scalar.times(r, _))
}

@SerialVersionUID(0L)
class MapVectorSpace[K, V](override implicit val scalar: Field[V]) extends MapRng[K, V]
with VectorSpace[Map[K, V], V] with Serializable {
  override def times(x: Map[K, V], y: Map[K, V]): Map[K, V] = {
    var xx = x
    var yy = y
    var f = scalar.times _
    if (x.size < y.size) { xx = y; yy = x }
    yy.foldLeft(zero) { (z, kv) =>
      (xx get kv._1).map(u => z.updated(kv._1, scalar.times(u, kv._2))).getOrElse(z)
    }
  }
}

@SerialVersionUID(0L)
class MapInnerProductSpace[K, V: Field] extends MapVectorSpace[K, V]
with InnerProductSpace[Map[K, V], V] with Serializable {
  def dot(x: Map[K, V], y: Map[K, V]): V =
    times(x, y).foldLeft(scalar.zero) { (a, b) => scalar.plus(a, b._2) }
}

@SerialVersionUID(0L)
class MapBasis[I, K](implicit K: AdditiveMonoid[K]) extends IndexedBasis[Map[I, K], K, I] with Serializable {
  type Idx = I

  private def zero: K = K.zero

  def hasKnownSize: Boolean = false
  def size: Int = ???

  def coord(v: Map[I, K], i: I): K = v(i)

  def map(v: Map[I, K])(f: K => K): Map[I, K] =
    v map { case (i, k) => (i, f(k)) }

  def mapWithIndex(v: Map[I, K])(f: (Idx, K) => K): Map[I, K] =
    v map { case (i, k) => (i, f(i, k)) }

  def zipMap(v: Map[I, K], w: Map[I, K])(f: (K, K) => K): Map[I, K] =
    zipMapWithIndex(v, w)((i, k0, k1) => f(k0, k1))

  def zipMapWithIndex(v: Map[I, K], w: Map[I, K])(f: (I, K, K) => K): Map[I, K] =
    MapJoin.Custom[I, K]((i, l) => f(i, l, zero), (i, r) => f(i, zero, r), f)(v, w)

  def foreachNonZero[U](v: Map[I, K])(f: K => U): Unit =
    v foreach { case (i, k) => f(k) }

  def foreachNonZeroWithIndex[U](v: Map[I, K])(f: (I, K) => U): Unit =
    v foreach f.tupled
}

@SerialVersionUID(0L)
class MapEq[K, V](implicit V: Eq[V]) extends Eq[Map[K, V]] with Serializable {
  def eqv(x: Map[K, V], y: Map[K, V]): Boolean = {
    if (x.size != y.size) false else {
      x forall { case (k, v) =>
        (y get k) match {
          case Some(e) if V.eqv(e, v) => true
          case _ => false
        }
      }
    }
  }
}

@SerialVersionUID(0L)
class MapVectorEq[K, V](implicit V: Eq[V], scalar: AdditiveMonoid[V]) extends Eq[Map[K, V]] with Serializable {
  def eqv(x: Map[K, V], y: Map[K, V]): Boolean = {
    @tailrec
    def loop(acc: Map[K, V], it: Iterator[(K, V)]): Boolean = {
      if (it.hasNext) {
        val (k, v0) = it.next()
        (acc get k) match {
          case Some(v1) if V.eqv(v0, v1) =>
            loop(acc - k, it)
          case None if V.eqv(v0, scalar.zero) =>
            loop(acc - k, it)
          case _ =>
            false
        }
      } else {
        acc forall { case (_, v) => V.eqv(v, scalar.zero) }
      }
    }

    loop(x, y.toIterator)
  }
}

trait MapInstances0 {
  implicit def MapMonoid[K, V: Semigroup] = new MapMonoid[K, V]

  implicit def MapRng[K, V: Rng] = new MapRng[K, V]

  implicit def MapBasis[K, V: AdditiveMonoid] = new MapBasis[K, V]
}

trait MapInstances1 extends MapInstances0 {
  implicit def MapGroup[K, V: Group] = new MapGroup[K, V]

  implicit def MapVectorSpace[K, V: Field] = new MapVectorSpace[K, V]
}

trait MapInstances2 extends MapInstances1 {
  implicit def MapInnerProductSpace[K, V: Field] = new MapInnerProductSpace[K, V]

  implicit def MapEq[K, V](implicit V0: Eq[V]) = new MapEq[K, V]
}

trait MapInstances extends MapInstances2
