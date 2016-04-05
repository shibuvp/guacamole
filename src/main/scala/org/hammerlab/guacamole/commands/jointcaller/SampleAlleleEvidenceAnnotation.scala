package org.hammerlab.guacamole.commands.jointcaller

import java.util

import htsjdk.variant.variantcontext.GenotypeBuilder
import htsjdk.variant.vcf.{VCFFilterHeaderLine, VCFFormatHeaderLine, VCFHeaderLine, VCFHeaderLineType}
import org.hammerlab.guacamole.filters.FishersExactTest

trait SampleAlleleEvidenceAnnotation {
  val parameters: Parameters
  def isFiltered: Boolean = false
  def addInfoToVCF(builder: GenotypeBuilder): Unit
}

object SampleAlleleEvidenceAnnotation {
  type NamedAnnotations = Map[String, SampleAlleleEvidenceAnnotation]
  val emptyAnnotations = Map[String, SampleAlleleEvidenceAnnotation]()

  val availableAnnotations: Seq[Metadata] = Seq(StrandBias)

  trait Metadata {
    val name: String
    def addVCFHeaders(headerLines: util.Set[VCFHeaderLine]): Unit
    def apply(stats: PileupStats, evidence: SampleAlleleEvidence, parameters: Parameters): SampleAlleleEvidenceAnnotation
  }

  def annotate(stats: PileupStats, evidence: SampleAlleleEvidence, parameters: Parameters): SampleAlleleEvidence = {
    val namedAnnotations = availableAnnotations.map(
      annotation => (annotation.name, annotation(stats, evidence, parameters))).toMap
    evidence.withAnnotations(namedAnnotations)
  }

  def addVCFHeaders(headerLines: util.Set[VCFHeaderLine]): Unit = {
    availableAnnotations.foreach(annotation => annotation.addVCFHeaders(headerLines))
  }

  case class StrandBias(
      parameters: Parameters,
      variantForward: Int,
      variantReverse: Int,
      totalForward: Int,
      totalReverse: Int) extends SampleAlleleEvidenceAnnotation {

    val phredValue = 10.0 * FishersExactTest.asLog10(totalForward, totalReverse, variantForward, variantReverse)

    override val isFiltered = phredValue > parameters.filterStrandBiasPhred

    def addInfoToVCF(builder: GenotypeBuilder): Unit = {
      builder.attribute("FS", phredValue.round.toInt)
    }
  }
  object StrandBias extends Metadata {
    val name = "STRAND_BIAS"
    def addVCFHeaders(headerLines: util.Set[VCFHeaderLine]): Unit = {
      headerLines.add(new VCFFormatHeaderLine("FS", 1, VCFHeaderLineType.Integer, "Phred scaled strand bias"))
      headerLines.add(new VCFFilterHeaderLine(name, "Phred scaled strand bias (FS) exceeds threshold"))
    }

    def apply(stats: PileupStats, evidence: SampleAlleleEvidence, parameters: Parameters): StrandBias = {
      val variantReads = stats.alleleToSubsequences.getOrElse(evidence.allele.alt, Seq.empty)
      val variantForward = variantReads.count(_.read.isPositiveStrand)
      val totalForward = stats.subsequences.count(_.read.isPositiveStrand)
      StrandBias(
        parameters,
        variantForward = variantForward,
        variantReverse = variantReads.length - variantForward,
        totalForward = totalForward,
        totalReverse = stats.subsequences.length - totalForward
      )
    }
  }
}