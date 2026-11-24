import { ArrowRight, Bone, ChevronRight, ScanLine, TestTube } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { cn } from "@/lib/utils";
import { publicServiceCategories } from "@/lib/routes/publicServices";

const visuals = {
  EXAMINATION: { icon: Bone, image: "clinic-consultation.jpg" },
  IMAGING: { icon: ScanLine, image: "imaging-service.webp" },
  LABORATORY: { icon: TestTube, image: "laboratory-service.webp" },
};

export function HomeServiceCategories() {
  return (
    <section
      aria-labelledby="home-services-title"
      id="dich-vu"
      className="scroll-mt-32"
    >
      <div className={cn(publicContainerClass, "py-6 md:py-10")}>
        <h2
          id="home-services-title"
          className="mb-3 text-2xl font-bold tracking-tight text-brand-hover md:mb-5 lg:text-4xl"
        >
          Dịch vụ tại phòng khám
        </h2>
        <ul className="grid gap-2 md:grid-cols-3 md:gap-5">
          {publicServiceCategories.map(({ type, label, href }) => {
            const { icon: Icon, image } = visuals[type];
            return (
              <li className="min-w-0" key={type}>
                <Link
                  href={href}
                  className="group flex h-full overflow-hidden rounded-xl border bg-card shadow-sm outline-none hover:border-primary/60 focus-visible:ring-3 focus-visible:ring-ring md:flex-col"
                >
                  <div className="relative m-1 w-[31%] max-w-40 shrink-0 overflow-hidden rounded-lg bg-secondary md:m-0 md:aspect-[16/10] md:w-full md:max-w-none md:rounded-none">
                    <Image
                      alt=""
                      className="object-cover"
                      fill
                      sizes="(min-width: 768px) 33vw, (min-width: 520px) 160px, 31vw"
                      src={`/images/home/${image}`}
                    />
                  </div>
                  <div className="flex min-h-[4.5rem] min-w-0 flex-1 items-center gap-2 px-2 py-2 md:min-h-20 md:gap-3 md:px-4">
                    <span className="hidden size-10 shrink-0 items-center justify-center rounded-full bg-secondary text-primary xl:flex">
                      <Icon aria-hidden className="size-6" />
                    </span>
                    <span className="min-w-0 text-base font-bold">{label}</span>
                    <ChevronRight
                      aria-hidden
                      className="ml-auto size-5 shrink-0 text-primary md:hidden"
                    />
                    <ArrowRight
                      aria-hidden
                      className="ml-auto hidden size-5 shrink-0 text-primary md:block"
                    />
                  </div>
                </Link>
              </li>
            );
          })}
        </ul>
      </div>
    </section>
  );
}
